const express = require("express");
const crypto = require("crypto");

function createAdminRouter(options) {
  const {
    adminToken,
    adminTokenHash,
    adminIpAllowlist,
    adminBlockRules,
    getCaMetrics,
    clearCaCache,
    warmupCaEntries,
    getCaConfig,
    logger
  } = options;

  const router = express.Router();

  const allowlistEntries = Array.isArray(adminIpAllowlist)
    ? adminIpAllowlist.map((ip) => String(ip || "").trim()).filter(Boolean)
    : [];
  const allowlistExact = new Set(
    allowlistEntries
      .filter((entry) => !entry.includes("/") && !entry.includes(":"))
      .map((entry) => normalizeIp(entry))
  );
  const allowlistExactIpv6 = allowlistEntries
    .filter((entry) => !entry.includes("/") && entry.includes(":"))
    .map((entry) => ipv6ToBigInt(normalizeIp(entry)))
    .filter((entry) => entry !== null);
  const allowlistCidrV4 = allowlistEntries
    .filter((entry) => entry.includes("/"))
    .map((entry) => parseIpv4Cidr(entry))
    .filter(Boolean);
  const allowlistCidrV6 = allowlistEntries
    .filter((entry) => entry.includes("/"))
    .map((entry) => parseIpv6Cidr(entry))
    .filter(Boolean);

  const blockRules = Array.isArray(adminBlockRules)
    ? adminBlockRules.map((rule) => parseBlockRule(rule)).filter(Boolean)
    : [];

  function hashToken(token) {
    return crypto.createHash("sha256").update(token).digest("hex");
  }

  function timingSafeEquals(left, right) {
    const leftBuffer = Buffer.from(left, "utf8");
    const rightBuffer = Buffer.from(right, "utf8");
    if (leftBuffer.length !== rightBuffer.length) return false;
    return crypto.timingSafeEqual(leftBuffer, rightBuffer);
  }

  function normalizeIp(ip) {
    const normalized = String(ip || "").trim().toLowerCase();
    if (normalized.startsWith("::ffff:")) {
      return normalized.slice("::ffff:".length);
    }
    return normalized;
  }

  function parseBlockRule(rule) {
    const normalized = String(rule || "").trim();
    if (!normalized.includes(":")) {
      return null;
    }

    const separatorIndex = normalized.indexOf(":");
    const method = normalized.slice(0, separatorIndex).trim().toUpperCase();
    const path = normalized.slice(separatorIndex + 1).trim();
    if (!method || !path.startsWith("/")) {
      return null;
    }

    return {
      method,
      path
    };
  }

  function ipv4ToInt(ip) {
    const parts = ip.split(".").map((part) => Number(part));
    if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
      return null;
    }

    return (((parts[0] << 24) >>> 0) + (parts[1] << 16) + (parts[2] << 8) + parts[3]) >>> 0;
  }

  function parseIpv4Cidr(cidr) {
    const [network, prefixString] = cidr.split("/");
    const prefix = Number(prefixString);
    const networkInt = ipv4ToInt(network);
    if (networkInt === null || !Number.isInteger(prefix) || prefix < 0 || prefix > 32) {
      return null;
    }

    const mask = prefix === 0 ? 0 : ((0xffffffff << (32 - prefix)) >>> 0);
    return {
      cidr,
      mask,
      network: (networkInt & mask) >>> 0
    };
  }

  function ipv6ToBigInt(ip) {
    const normalized = normalizeIp(ip);
    if (!normalized.includes(":")) {
      return null;
    }

    const [head, tail] = normalized.split("::");
    const headParts = head ? head.split(":").filter(Boolean) : [];
    const tailParts = tail ? tail.split(":").filter(Boolean) : [];

    if (headParts.length + tailParts.length > 8) {
      return null;
    }

    const missingGroups = 8 - (headParts.length + tailParts.length);
    const groups = normalized.includes("::")
      ? [...headParts, ...Array(missingGroups).fill("0"), ...tailParts]
      : normalized.split(":");

    if (groups.length !== 8) {
      return null;
    }

    let value = 0n;
    for (const group of groups) {
      const parsed = Number.parseInt(group || "0", 16);
      if (!Number.isInteger(parsed) || parsed < 0 || parsed > 0xffff) {
        return null;
      }

      value = (value << 16n) + BigInt(parsed);
    }

    return value;
  }

  function parseIpv6Cidr(cidr) {
    const [network, prefixString] = cidr.split("/");
    const prefix = Number(prefixString);
    const networkInt = ipv6ToBigInt(network);
    if (networkInt === null || !Number.isInteger(prefix) || prefix < 0 || prefix > 128) {
      return null;
    }

    const allBits = (1n << 128n) - 1n;
    const mask = prefix === 0 ? 0n : ((allBits << BigInt(128 - prefix)) & allBits);
    return {
      cidr,
      mask,
      network: networkInt & mask
    };
  }

  function isIpAllowed(ip) {
    const normalized = normalizeIp(ip);
    if (allowlistExact.has(normalized)) {
      return true;
    }

    const ipv6 = ipv6ToBigInt(normalized);
    if (ipv6 !== null) {
      if (allowlistExactIpv6.some((entry) => entry === ipv6)) {
        return true;
      }

      return allowlistCidrV6.some((entry) => (ipv6 & entry.mask) === entry.network);
    }

    const ipInt = ipv4ToInt(normalized);
    if (ipInt === null) return false;

    return allowlistCidrV4.some((entry) => ((ipInt & entry.mask) >>> 0) === entry.network);
  }

  function validateBlockedRules(req, res, next) {
    if (blockRules.length === 0) {
      return next();
    }

    const currentMethod = String(req.method || "").toUpperCase();
    const currentPath = String(req.path || "");
    const blocked = blockRules.find((rule) => {
      if (rule.method !== currentMethod) return false;
      if (rule.path.endsWith("*")) {
        return currentPath.startsWith(rule.path.slice(0, -1));
      }
      return currentPath === rule.path;
    });

    if (!blocked) {
      return next();
    }

    req.logger?.warn("admin_route_blocked", {
      method: currentMethod,
      path: currentPath,
      rule: `${blocked.method}:${blocked.path}`
    });

    return res.status(403).json({
      error: "forbidden_route",
      message: "Rota administrativa bloqueada por politica.",
      request_id: req.requestId
    });
  }

  function validateIpAllowlist(req, res, next) {
    if (allowlistEntries.length === 0) {
      return next();
    }

    const forwarded = String(req.headers["x-forwarded-for"] || "")
      .split(",")
      .map((item) => normalizeIp(item))
      .filter(Boolean);

    const directCandidates = [
      normalizeIp(req.ip),
      normalizeIp(req.socket?.remoteAddress)
    ].filter(Boolean);
    const candidates = forwarded.length > 0 ? forwarded : directCandidates;

    const authorized = candidates.some((ip) => isIpAllowed(ip));
    if (!authorized) {
      req.logger?.warn("admin_ip_forbidden", {
        path: req.originalUrl,
        ip_candidates: candidates
      });

      return res.status(403).json({
        error: "forbidden_ip",
        message: "IP nao autorizado para area administrativa.",
        request_id: req.requestId
      });
    }

    return next();
  }

  function validateAdminToken(req, res, next) {
    const expectedHash = String(adminTokenHash || "").trim().toLowerCase();
    const expectedToken = String(adminToken || "").trim();
    if (!expectedHash && !expectedToken) {
      return res.status(503).json({
        error: "admin_not_configured",
        message: "Configure CA_ADMIN_TOKEN_SHA256 (ou CA_ADMIN_TOKEN) para habilitar rotas administrativas.",
        request_id: req.requestId
      });
    }

    const providedToken = String(
      req.headers["x-admin-token"] || req.query.token || ""
    ).trim();

    const providedHash = hashToken(providedToken);
    const authOk = expectedHash.length > 0
      ? timingSafeEquals(providedHash, expectedHash)
      : timingSafeEquals(providedToken, expectedToken);

    if (!authOk) {
      req.logger?.warn("admin_auth_failed", { path: req.originalUrl });
      return res.status(403).json({
        error: "forbidden",
        message: "Token administrativo invalido.",
        request_id: req.requestId
      });
    }

    return next();
  }

  router.use(validateIpAllowlist);
  router.use(validateBlockedRules);
  router.use(validateAdminToken);

  router.get("/metrics", (req, res) => {
    res.json({
      ok: true,
      service: "anda-ca-backend",
      metrics: getCaMetrics(),
      config: getCaConfig(),
      uptime_seconds: Math.round(process.uptime()),
      request_id: req.requestId
    });
  });

  router.post("/cache/clear", async (req, res) => {
    const result = await clearCaCache(req.logger);
    logger?.warn("admin_cache_cleared", {
      request_id: req.requestId,
      removed_entries: result.removedEntries
    });

    res.json({
      ok: true,
      removed_entries: result.removedEntries,
      request_id: req.requestId
    });
  });

  router.post("/cache/warmup", async (req, res) => {
    const cas = Array.isArray(req.body?.cas) ? req.body.cas : [];
    const result = await warmupCaEntries(cas, req.logger);

    res.json({
      ok: true,
      warmed: result.warmed,
      ignored: result.ignored,
      cache_entries: result.cacheEntries,
      request_id: req.requestId
    });
  });

  router.get("/dashboard", (_req, res) => {
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.send(`<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Anda C.A. - Admin Dashboard</title>
    <style>
      body { font-family: Arial, sans-serif; margin: 24px; color: #202124; }
      h1 { margin-bottom: 8px; }
      .box { border: 1px solid #dadce0; border-radius: 8px; padding: 16px; margin-top: 12px; }
      button { padding: 8px 12px; cursor: pointer; }
      pre { white-space: pre-wrap; word-break: break-word; }
      .muted { color: #5f6368; }
    </style>
  </head>
  <body>
    <h1>Anda C.A. - Dashboard</h1>
    <p class="muted">Atualizacao automatica a cada 5 segundos.</p>
    <div class="box">
      <button id="clearCache">Limpar cache</button>
      <button id="warmupCache">Aquecer cache</button>
      <input id="warmupInput" placeholder="Ex.: 12345,98765" style="width: 100%; margin-top: 8px; padding: 8px;" />
      <p id="status" class="muted"></p>
    </div>
    <div class="box">
      <h3>Metricas</h3>
      <pre id="metrics">Carregando...</pre>
    </div>
    <div class="box">
      <h3>Tendencia (cache hits e fallback)</h3>
      <canvas id="trendChart" width="640" height="220" style="max-width: 100%; border: 1px solid #dadce0;"></canvas>
    </div>

    <script>
      const params = new URLSearchParams(window.location.search);
      const token = params.get("token") || "";
      const headers = token ? { "X-Admin-Token": token } : {};
      const statusEl = document.getElementById("status");
      const metricsEl = document.getElementById("metrics");
      const warmupInput = document.getElementById("warmupInput");
      const chartCanvas = document.getElementById("trendChart");
      const chartCtx = chartCanvas.getContext("2d");
      const metricHistory = [];

      function drawChart() {
        chartCtx.clearRect(0, 0, chartCanvas.width, chartCanvas.height);
        chartCtx.fillStyle = "#202124";
        chartCtx.fillText("cacheHits", 12, 16);
        chartCtx.fillStyle = "#d93025";
        chartCtx.fillText("fallbackServed", 100, 16);

        if (metricHistory.length < 2) {
          return;
        }

        const maxValue = Math.max(1, ...metricHistory.map((item) => Math.max(item.cacheHits, item.fallbackServed)));
        const stepX = (chartCanvas.width - 40) / (metricHistory.length - 1);

        function drawLine(color, selector) {
          chartCtx.beginPath();
          chartCtx.strokeStyle = color;
          chartCtx.lineWidth = 2;

          metricHistory.forEach((item, index) => {
            const x = 20 + (index * stepX);
            const y = chartCanvas.height - 20 - ((selector(item) / maxValue) * (chartCanvas.height - 50));
            if (index === 0) chartCtx.moveTo(x, y);
            else chartCtx.lineTo(x, y);
          });
          chartCtx.stroke();
        }

        drawLine("#202124", (item) => item.cacheHits);
        drawLine("#d93025", (item) => item.fallbackServed);
      }

      async function loadMetrics() {
        try {
          const response = await fetch("/admin/metrics", { headers });
          const payload = await response.json();
          metricsEl.textContent = JSON.stringify(payload, null, 2);
          statusEl.textContent = response.ok ? "OK" : (payload.message || "Falha na consulta de metricas");
          if (response.ok) {
            metricHistory.push({
              cacheHits: payload.metrics.cacheHits || 0,
              fallbackServed: payload.metrics.fallbackServed || 0
            });
            if (metricHistory.length > 30) metricHistory.shift();
            drawChart();
          }
        } catch (error) {
          statusEl.textContent = "Erro: " + error.message;
        }
      }

      document.getElementById("clearCache").addEventListener("click", async () => {
        try {
          const response = await fetch("/admin/cache/clear", { method: "POST", headers });
          const payload = await response.json();
          statusEl.textContent = response.ok
            ? "Cache limpo (" + payload.removed_entries + " entradas)."
            : (payload.message || "Falha ao limpar cache");
          await loadMetrics();
        } catch (error) {
          statusEl.textContent = "Erro: " + error.message;
        }
      });

      document.getElementById("warmupCache").addEventListener("click", async () => {
        const values = warmupInput.value
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean);

        if (values.length === 0) {
          statusEl.textContent = "Informe ao menos um C.A. para aquecimento.";
          return;
        }

        try {
          const response = await fetch("/admin/cache/warmup", {
            method: "POST",
            headers: {
              ...headers,
              "Content-Type": "application/json"
            },
            body: JSON.stringify({ cas: values })
          });
          const payload = await response.json();
          statusEl.textContent = response.ok
            ? "Warmup finalizado: " + payload.warmed + " aquecidos, " + payload.ignored + " ignorados."
            : (payload.message || "Falha no aquecimento de cache");
          await loadMetrics();
        } catch (error) {
          statusEl.textContent = "Erro: " + error.message;
        }
      });

      loadMetrics();
      setInterval(loadMetrics, 5000);
    </script>
  </body>
</html>`);
  });

  return router;
}

module.exports = {
  createAdminRouter
};

