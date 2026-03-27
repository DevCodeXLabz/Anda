const express = require("express");
const dotenv = require("dotenv");
const { createLogger } = require("./lib/logger");
const {
  getCaMetrics,
  getHomeMetrics,
  getCaConfig,
  clearCaCache,
  warmupCaEntries,
  setCaProviderLogger
} = require("./services/caProvider");
const caRoutes = require("./routes/ca");
const { createAdminRouter } = require("./routes/admin");

dotenv.config();

function createRateLimiter(options = {}) {
  const windowMs = Number(options.rateLimitWindowMs ?? process.env.CA_RATE_LIMIT_WINDOW_MS ?? 60 * 1000);
  const maxRequests = Number(options.rateLimitMax ?? process.env.CA_RATE_LIMIT_MAX ?? 60);
  const buckets = new Map();

  return (req, res, next) => {
    const logger = req.logger || req.app.locals.logger;
    const now = Date.now();
    const key = req.ip || req.headers["x-forwarded-for"] || "unknown";
    const bucket = buckets.get(key);

    if (!bucket || bucket.resetAt <= now) {
      buckets.set(key, { count: 1, resetAt: now + windowMs });
      res.setHeader("X-RateLimit-Limit", String(maxRequests));
      return next();
    }

    if (bucket.count >= maxRequests) {
      const retryAfterSeconds = Math.max(1, Math.ceil((bucket.resetAt - now) / 1000));
      res.setHeader("Retry-After", String(retryAfterSeconds));
      res.setHeader("X-RateLimit-Limit", String(maxRequests));
      logger?.warn("rate_limited", {
        request_id: req.requestId,
        ip: key,
        retry_after_seconds: retryAfterSeconds
      });
      return res.status(429).json({
        error: "rate_limited",
        message: "Muitas consultas em pouco tempo. Tente novamente em instantes.",
        retry_after_seconds: retryAfterSeconds,
        request_id: req.requestId
      });
    }

    bucket.count += 1;
    res.setHeader("X-RateLimit-Limit", String(maxRequests));
    return next();
  };
}

function createApp(options = {}) {
  const app = express();
  let requestCounter = 0;
  const logger = options.logger || createLogger();
  const adminToken = options.adminToken ?? process.env.CA_ADMIN_TOKEN;
  const adminTokenHash = options.adminTokenHash ?? process.env.CA_ADMIN_TOKEN_SHA256;
  const adminIpAllowlist = options.adminIpAllowlist ?? String(process.env.CA_ADMIN_IP_ALLOWLIST || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  const adminBlockRules = options.adminBlockRules ?? String(process.env.CA_ADMIN_BLOCK_RULES || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  const warmupOnBoot = options.warmupOnBoot ?? String(process.env.CA_WARMUP_ON_BOOT || "false").toLowerCase() === "true";
  const warmupCas = options.warmupCas ?? String(process.env.CA_WARMUP_CAS || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

  app.use(express.json());
  app.locals.logger = logger;
  setCaProviderLogger(logger.child({ scope: "ca_provider" }));
  app.use((req, res, next) => {
    requestCounter += 1;
    const startTime = Date.now();
    req.requestId = `req-${Date.now()}-${requestCounter}`;
    req.logger = logger.child({ request_id: req.requestId });
    res.setHeader("X-Request-Id", req.requestId);
    req.logger.info("request_started", {
      method: req.method,
      path: req.originalUrl
    });
    res.on("finish", () => {
      req.logger.info("request_completed", {
        method: req.method,
        path: req.originalUrl,
        status_code: res.statusCode,
        duration_ms: Date.now() - startTime
      });
    });
    next();
  });

  app.get("/health", (req, res) => {
    res.json({
      ok: true,
      service: "anda-ca-backend",
      metrics: getCaMetrics()
    });
    req.logger.info("health_checked", {
      metrics: getCaMetrics()
    });
  });

  app.get("/home/metrics", (req, res) => {
    const metrics = getHomeMetrics();
    res.json({
      ...metrics,
      request_id: req.requestId
    });
  });

  app.use("/ca", createRateLimiter(options));
  app.use(caRoutes);
  app.use("/admin", createAdminRouter({
    adminToken,
    adminTokenHash,
    adminIpAllowlist,
    adminBlockRules,
    getCaMetrics,
    getCaConfig,
    clearCaCache,
    warmupCaEntries,
    logger
  }));

  if (warmupOnBoot && warmupCas.length > 0) {
    const warmupLogger = logger.child({ scope: "boot_warmup" });
    setImmediate(() => {
      warmupLogger.info("boot_warmup_started", { total: warmupCas.length });
      warmupCaEntries(warmupCas, warmupLogger)
        .then((result) => {
          warmupLogger.info("boot_warmup_finished", result);
        })
        .catch((error) => {
          warmupLogger.error("boot_warmup_failed", { error: error.message });
        });
    });
  }

  return app;
}

if (require.main === module) {
  const port = Number(process.env.PORT || 8080);
  const app = createApp();
  app.listen(port, () => {
    app.locals.logger.info("server_started", { port });
  });
}

module.exports = {
  createApp
};

