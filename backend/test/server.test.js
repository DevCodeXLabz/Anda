const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const { createLogger } = require("../src/lib/logger");
const { createApp } = require("../src/server");
const { resetCaState } = require("../src/services/caProvider");

async function withServer(run, options = {}) {
  resetCaState();
  const app = createApp(options);
  const server = app.listen(0);

  try {
    await new Promise((resolve, reject) => {
      server.once("listening", resolve);
      server.once("error", reject);
    });

    const { port } = server.address();
    await run(`http://127.0.0.1:${port}`);
  } finally {
    await new Promise((resolve, reject) => {
      server.close((error) => {
        if (error) reject(error);
        else resolve();
      });
    });
  }
}

test("GET /health returns service status", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/health`);
    assert.equal(response.status, 200);
    assert.equal(typeof response.headers.get("x-request-id"), "string");

    const payload = await response.json();
    assert.equal(payload.ok, true);
    assert.equal(payload.service, "anda-ca-backend");
    assert.equal(payload.metrics.cacheEntries, 0);
  });
});

test("GET /home/metrics returns compact fallback window summary", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/home/metrics`);
    assert.equal(response.status, 200);

    const payload = await response.json();
    assert.equal(typeof payload.backend_online, "boolean");
    assert.equal(typeof payload.fallback_level, "string");
    assert.equal(typeof payload.fallback_rate_window, "number");
    assert.equal(typeof payload.sample_size_window, "number");
  });
});

test("GET /home/metrics reaches CRITICAL after repeated fallback lookups", async () => {
  await withServer(async (baseUrl) => {
    for (let i = 0; i < 5; i += 1) {
      await fetch(`${baseUrl}/ca/12345?i=${i}`);
    }

    const response = await fetch(`${baseUrl}/home/metrics`);
    const payload = await response.json();

    assert.equal(response.status, 200);
    assert.equal(payload.fallback_level, "CRITICAL");
    assert.equal(payload.sample_size_window >= 5, true);
  });
});

test("GET /ca/:numero returns normalized fallback payload", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/ca/12345`);
    assert.equal(response.status, 200);

    const payload = await response.json();
    assert.equal(payload.ca, "12345");
    assert.equal(payload.is_valid, true);
    assert.equal(payload.item_name, "Protetor auricular tipo plug");
    assert.equal(payload.source, "fallback-local");
    assert.equal(typeof payload.request_id, "string");
    assert.equal(payload.cached, false);
  });
});

test("GET /ca/:numero serves cached payload on repeated lookup", async () => {
  await withServer(async (baseUrl) => {
    await fetch(`${baseUrl}/ca/12345`);
    const secondResponse = await fetch(`${baseUrl}/ca/12345`);
    const secondPayload = await secondResponse.json();

    assert.equal(secondResponse.status, 200);
    assert.equal(secondPayload.cached, true);
  });
});

test("GET /ca/:numero rejects invalid CA number", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/ca/ab12`);
    assert.equal(response.status, 400);

    const payload = await response.json();
    assert.equal(payload.error, "invalid_ca_number");
  });
});

test("GET /ca/:numero applies rate limit after configured threshold", async () => {
  await withServer(async (baseUrl) => {
    const firstResponse = await fetch(`${baseUrl}/ca/12345`);
    assert.equal(firstResponse.status, 200);

    const secondResponse = await fetch(`${baseUrl}/ca/12345`);
    assert.equal(secondResponse.status, 429);

    const payload = await secondResponse.json();
    assert.equal(payload.error, "rate_limited");
    assert.equal(typeof payload.request_id, "string");
  }, { rateLimitMax: 1, rateLimitWindowMs: 60_000 });
});

test("requests emit structured logs with request context", async () => {
  const logs = [];
  const logger = createLogger({
    level: "debug",
    sink: (_line, _level, payload) => {
      logs.push(payload);
    }
  });

  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/ca/12345`);
    assert.equal(response.status, 200);
  }, { logger });

  assert.ok(logs.some((entry) => entry.message === "request_started"));
  assert.ok(logs.some((entry) => entry.message === "request_completed"));
  assert.ok(logs.some((entry) => entry.message === "ca_fallback_local_served"));
});

test("admin metrics route requires token when configured", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/admin/metrics`);
    assert.equal(response.status, 403);
  }, { adminToken: "segredo" });
});

test("admin route blocks non-allowlisted IP", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/admin/metrics`, {
      headers: {
        "X-Admin-Token": "segredo",
        "X-Forwarded-For": "203.0.113.5"
      }
    });

    assert.equal(response.status, 403);
    const payload = await response.json();
    assert.equal(payload.error, "forbidden_ip");
  }, {
    adminToken: "segredo",
    adminIpAllowlist: ["127.0.0.1", "::1"]
  });
});

test("admin route accepts allowlisted forwarded IP", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/admin/metrics`, {
      headers: {
        "X-Admin-Token": "segredo",
        "X-Forwarded-For": "127.0.0.1"
      }
    });

    assert.equal(response.status, 200);
  }, {
    adminToken: "segredo",
    adminIpAllowlist: ["127.0.0.1"]
  });
});

test("admin route accepts CIDR allowlist entry", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/admin/metrics`, {
      headers: {
        "X-Admin-Token": "segredo",
        "X-Forwarded-For": "10.10.5.17"
      }
    });

    assert.equal(response.status, 200);
  }, {
    adminToken: "segredo",
    adminIpAllowlist: ["10.10.0.0/16"]
  });
});

test("admin route accepts IPv6 CIDR allowlist entry", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/admin/metrics`, {
      headers: {
        "X-Admin-Token": "segredo",
        "X-Forwarded-For": "2001:db8:abcd::2"
      }
    });

    assert.equal(response.status, 200);
  }, {
    adminToken: "segredo",
    adminIpAllowlist: ["2001:db8:abcd::/48"]
  });
});

test("admin route block rules deny configured method and path", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/admin/cache/clear`, {
      method: "POST",
      headers: {
        "X-Admin-Token": "segredo",
        "X-Forwarded-For": "127.0.0.1"
      }
    });

    assert.equal(response.status, 403);
    const payload = await response.json();
    assert.equal(payload.error, "forbidden_route");
  }, {
    adminToken: "segredo",
    adminIpAllowlist: ["127.0.0.1"],
    adminBlockRules: ["POST:/cache/clear"]
  });
});

test("admin metrics route accepts SHA-256 token hash", async () => {
  const hash = crypto.createHash("sha256").update("segredo").digest("hex");

  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/admin/metrics`, {
      headers: { "X-Admin-Token": "segredo" }
    });

    assert.equal(response.status, 200);
  }, { adminTokenHash: hash });
});

test("admin metrics route returns metrics with valid token", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/admin/metrics`, {
      headers: { "X-Admin-Token": "segredo" }
    });
    assert.equal(response.status, 200);

    const payload = await response.json();
    assert.equal(payload.ok, true);
    assert.equal(payload.metrics.cacheEntries, 0);
    assert.equal(typeof payload.config.cache_file_path, "string");
  }, { adminToken: "segredo" });
});

test("admin cache clear removes cached entries", async () => {
  await withServer(async (baseUrl) => {
    const headers = { "X-Admin-Token": "segredo" };

    await fetch(`${baseUrl}/ca/12345`);
    const clearResponse = await fetch(`${baseUrl}/admin/cache/clear`, {
      method: "POST",
      headers
    });

    assert.equal(clearResponse.status, 200);
    const clearPayload = await clearResponse.json();
    assert.equal(clearPayload.ok, true);
    assert.equal(clearPayload.removed_entries >= 1, true);

    const metricsResponse = await fetch(`${baseUrl}/admin/metrics`, { headers });
    const metricsPayload = await metricsResponse.json();
    assert.equal(metricsPayload.metrics.cacheEntries, 0);
    assert.equal(metricsPayload.metrics.cacheClears >= 1, true);
  }, { adminToken: "segredo" });
});

test("admin cache warmup preloads valid CA numbers", async () => {
  await withServer(async (baseUrl) => {
    const headers = {
      "X-Admin-Token": "segredo",
      "Content-Type": "application/json"
    };

    const response = await fetch(`${baseUrl}/admin/cache/warmup`, {
      method: "POST",
      headers,
      body: JSON.stringify({ cas: ["12345", "abc", "98765"] })
    });

    assert.equal(response.status, 200);
    const payload = await response.json();
    assert.equal(payload.ok, true);
    assert.equal(payload.warmed, 2);
    assert.equal(payload.ignored, 1);

    const metricsResponse = await fetch(`${baseUrl}/admin/metrics`, {
      headers: { "X-Admin-Token": "segredo" }
    });
    const metricsPayload = await metricsResponse.json();
    assert.equal(metricsPayload.metrics.cacheEntries >= 2, true);
  }, { adminToken: "segredo" });
});

test("admin dashboard returns HTML when token is valid", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/admin/dashboard?token=segredo`);
    assert.equal(response.status, 200);
    const html = await response.text();
    assert.ok(html.includes("Anda C.A. - Dashboard"));
  }, { adminToken: "segredo" });
});

test("warmup on boot preloads cache entries", async () => {
  await withServer(async (baseUrl) => {
    await new Promise((resolve) => setTimeout(resolve, 60));
    const response = await fetch(`${baseUrl}/health`);
    const payload = await response.json();

    assert.equal(response.status, 200);
    assert.equal(payload.metrics.cacheEntries >= 1, true);
  }, {
    warmupOnBoot: true,
    warmupCas: ["12345", "abc"]
  });
});

