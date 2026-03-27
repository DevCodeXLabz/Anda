const { normalizeCaPayload } = require("../validators/caSchema");
const path = require("path");
const { loadCacheSnapshot, saveCacheSnapshot } = require("./cacheStore");

const SUCCESS_CACHE_TTL_MS = Number(process.env.CA_CACHE_TTL_MS || 5 * 60 * 1000);
const NEGATIVE_CACHE_TTL_MS = Number(process.env.CA_NEGATIVE_CACHE_TTL_MS || 60 * 1000);
const UPSTREAM_TIMEOUT_MS = Number(process.env.CA_UPSTREAM_TIMEOUT_MS || 4000);
const UPSTREAM_RETRIES = Number(process.env.CA_UPSTREAM_RETRIES || 1);
const FALLBACK_WINDOW_MS = Number(process.env.CA_FALLBACK_WINDOW_MS || 5 * 60 * 1000);
const CACHE_FILE_PATH = process.env.CA_CACHE_FILE_PATH || path.join(process.cwd(), ".cache", "ca-cache.json");
const CACHE_FLUSH_DEBOUNCE_MS = Number(process.env.CA_CACHE_FLUSH_DEBOUNCE_MS || 1000);
const CACHE_PERSIST_ENABLED = String(process.env.CA_CACHE_PERSIST_ENABLED || "true").toLowerCase() !== "false";

const responseCache = new Map();
const metrics = {
  cacheHits: 0,
  cacheMisses: 0,
  upstreamSuccess: 0,
  upstreamFailures: 0,
  fallbackServed: 0,
  persistentWrites: 0,
  persistFailures: 0,
  persistLoads: 0,
  cacheClears: 0
};

let providerLogger = null;
let persistTimer = null;
let persistInFlight = false;
let persistQueued = false;
const recentLookupEvents = [];

if (CACHE_PERSIST_ENABLED) {
  const restoredCache = loadCacheSnapshot(CACHE_FILE_PATH);
  if (restoredCache.size > 0) {
    metrics.persistLoads = 1;
    for (const [ca, value] of restoredCache.entries()) {
      responseCache.set(ca, value);
    }
  }
}

const localFallback = {
  "12345": {
    ca: "12345",
    is_valid: true,
    item_name: "Protetor auricular tipo plug",
    risk_coverage: "Ruido ocupacional",
    valid_until: "2028-12-31",
    source: "fallback-local"
  },
  "98765": {
    ca: "98765",
    is_valid: false,
    item_name: "Luva de raspa",
    risk_coverage: "Risco mecanico",
    valid_until: "2023-10-31",
    source: "fallback-local",
    status_reason: "expired"
  }
};

function getCaMetrics() {
  const window = getFallbackWindowStats();

  return {
    ...metrics,
    cacheEntries: responseCache.size,
    fallbackRateWindow: window.fallbackRate,
    fallbackSampleWindow: window.sampleSize,
    fallbackLevelWindow: window.level
  };
}

function getFallbackLevelFromRate(rate, sampleSize) {
  if (sampleSize < 5) return "LOW_SAMPLE";
  if (rate >= 0.50) return "CRITICAL";
  if (rate >= 0.35) return "HIGH";
  if (rate >= 0.20) return "MODERATE";
  return "NORMAL";
}

function pruneLookupEvents(now = Date.now()) {
  const threshold = now - FALLBACK_WINDOW_MS;
  while (recentLookupEvents.length > 0 && recentLookupEvents[0].timestamp < threshold) {
    recentLookupEvents.shift();
  }
}

function recordLookupEvent(source) {
  recentLookupEvents.push({
    timestamp: Date.now(),
    source
  });
  pruneLookupEvents();
}

function getFallbackWindowStats() {
  pruneLookupEvents();
  const sampleSize = recentLookupEvents.length;
  const fallbackCount = recentLookupEvents.filter((event) => event.source.startsWith("fallback")).length;
  const fallbackRate = sampleSize > 0 ? fallbackCount / sampleSize : 0;

  return {
    windowMs: FALLBACK_WINDOW_MS,
    sampleSize,
    fallbackCount,
    fallbackRate,
    level: getFallbackLevelFromRate(fallbackRate, sampleSize)
  };
}

function getHomeMetrics() {
  const window = getFallbackWindowStats();
  return {
    backend_online: true,
    fallback_rate_window: Number(window.fallbackRate.toFixed(4)),
    fallback_level: window.level,
    sample_size_window: window.sampleSize,
    fallback_count_window: window.fallbackCount,
    window_ms: window.windowMs,
    cache_entries: responseCache.size
  };
}

function getCaConfig() {
  return {
    cache_file_path: CACHE_FILE_PATH,
    cache_ttl_ms: SUCCESS_CACHE_TTL_MS,
    negative_cache_ttl_ms: NEGATIVE_CACHE_TTL_MS,
    upstream_timeout_ms: UPSTREAM_TIMEOUT_MS,
    upstream_retries: UPSTREAM_RETRIES,
    fallback_window_ms: FALLBACK_WINDOW_MS,
    cache_flush_debounce_ms: CACHE_FLUSH_DEBOUNCE_MS,
    cache_persist_enabled: CACHE_PERSIST_ENABLED
  };
}

function setCaProviderLogger(logger) {
  providerLogger = logger || null;
}

function resolveLogger(logger) {
  return logger || providerLogger;
}

async function flushPersist(logger) {
  const activeLogger = resolveLogger(logger);
  if (!CACHE_PERSIST_ENABLED) {
    return;
  }

  if (persistInFlight) {
    persistQueued = true;
    return;
  }

  persistInFlight = true;
  try {
    await saveCacheSnapshot(CACHE_FILE_PATH, responseCache);
    metrics.persistentWrites += 1;
    activeLogger?.debug("ca_cache_persisted", {
      cache_file_path: CACHE_FILE_PATH,
      entries: responseCache.size
    });
  } catch (error) {
    metrics.persistFailures += 1;
    activeLogger?.error("ca_cache_persist_failed", {
      cache_file_path: CACHE_FILE_PATH,
      error: error.message
    });
  } finally {
    persistInFlight = false;
    if (persistQueued) {
      persistQueued = false;
      await flushPersist(activeLogger);
    }
  }
}

function schedulePersist(logger) {
  if (!CACHE_PERSIST_ENABLED) {
    return;
  }

  const activeLogger = resolveLogger(logger);
  if (persistTimer) {
    return;
  }

  persistTimer = setTimeout(() => {
    persistTimer = null;
    void flushPersist(activeLogger);
  }, CACHE_FLUSH_DEBOUNCE_MS);
}

function resetCaState() {
  if (persistTimer) {
    clearTimeout(persistTimer);
    persistTimer = null;
  }

  persistInFlight = false;
  persistQueued = false;
  responseCache.clear();
  recentLookupEvents.length = 0;
  Object.keys(metrics).forEach((key) => {
    metrics[key] = 0;
  });
}

async function clearCaCache(logger) {
  const removedEntries = responseCache.size;
  responseCache.clear();
  metrics.cacheClears += 1;
  await flushPersist(logger);
  return { removedEntries };
}

function normalizeWarmupInput(cas) {
  if (!Array.isArray(cas)) return [];
  return [...new Set(cas.map((item) => String(item || "").trim()))]
    .filter((item) => /^\d{3,}$/.test(item));
}

async function warmupCaEntries(cas, logger) {
  const activeLogger = resolveLogger(logger);
  const normalized = normalizeWarmupInput(cas);
  let warmed = 0;

  for (const ca of normalized) {
    await lookupCa(ca, activeLogger?.child ? activeLogger.child({ ca, mode: "warmup" }) : activeLogger);
    warmed += 1;
  }

  activeLogger?.info("ca_cache_warmup_finished", {
    requested: Array.isArray(cas) ? cas.length : 0,
    warmed
  });

  return {
    warmed,
    ignored: Array.isArray(cas) ? cas.length - warmed : 0,
    cacheEntries: responseCache.size
  };
}

function readCache(caNumber, logger) {
  const cached = responseCache.get(caNumber);
  if (!cached) {
    metrics.cacheMisses += 1;
    logger?.debug("ca_cache_miss", { ca: caNumber });
    return null;
  }

  if (cached.expiresAt <= Date.now()) {
    responseCache.delete(caNumber);
    metrics.cacheMisses += 1;
    logger?.info("ca_cache_expired", { ca: caNumber });
    schedulePersist(logger);
    return null;
  }

  metrics.cacheHits += 1;
  logger?.info("ca_cache_hit", { ca: caNumber });
  return {
    ...cached.payload,
    cached: true
  };
}

function writeCache(caNumber, payload, logger) {
  const ttl = payload.is_valid
    ? SUCCESS_CACHE_TTL_MS
    : NEGATIVE_CACHE_TTL_MS;

  responseCache.set(caNumber, {
    payload: {
      ...payload,
      cached: false
    },
    expiresAt: Date.now() + ttl
  });

  logger?.debug("ca_cache_write", {
    ca: caNumber,
    ttl_ms: ttl,
    source: payload.source,
    is_valid: payload.is_valid
  });
  schedulePersist(logger);

  return {
    ...payload,
    cached: false
  };
}

async function fetchJsonWithTimeout(url) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);

  try {
    return await fetch(url, {
      headers: { Accept: "application/json" },
      signal: controller.signal
    });
  } finally {
    clearTimeout(timeoutId);
  }
}

async function fetchWithRetry(caNumber, logger) {
  let lastError = null;

  for (let attempt = 0; attempt <= UPSTREAM_RETRIES; attempt += 1) {
    try {
      logger?.info("ca_upstream_attempt", {
        ca: caNumber,
        attempt: attempt + 1,
        max_attempts: UPSTREAM_RETRIES + 1
      });
      return await fetchFromUpstream(caNumber);
    } catch (error) {
      lastError = error;
      logger?.warn("ca_upstream_attempt_failed", {
        ca: caNumber,
        attempt: attempt + 1,
        error: error.message
      });
    }
  }

  throw lastError;
}

async function fetchFromUpstream(caNumber) {
  const upstreamBaseUrl = (process.env.CA_UPSTREAM_URL || "").trim();
  if (!upstreamBaseUrl) return null;

  const normalizedBase = upstreamBaseUrl.endsWith("/")
    ? upstreamBaseUrl.slice(0, -1)
    : upstreamBaseUrl;

  const response = await fetchJsonWithTimeout(`${normalizedBase}/ca/${caNumber}`);

  if (!response.ok) {
    if (response.status >= 500) {
      throw new Error(`Upstream returned ${response.status}`);
    }
    return null;
  }

  const payload = await response.json();
  return {
    ca: payload.ca ?? caNumber,
    is_valid: payload.is_valid ?? payload.valido ?? false,
    item_name: payload.item_name ?? payload.equipamento ?? payload.item ?? "-",
    risk_coverage: payload.risk_coverage ?? payload.protecao ?? "-",
    valid_until: payload.valid_until ?? payload.validade ?? "-",
    source: "upstream",
    status_reason: payload.status_reason ?? null
  };
}

async function lookupCa(caNumber, logger) {
  const cached = readCache(caNumber, logger);
  if (cached) {
    recordLookupEvent(cached.source || "unknown");
    return cached;
  }

  let upstreamUnavailable = false;

  try {
    const upstream = await fetchWithRetry(caNumber, logger);
    if (upstream) {
      metrics.upstreamSuccess += 1;
      logger?.info("ca_upstream_success", { ca: caNumber });
      const result = writeCache(caNumber, normalizeCaPayload(upstream), logger);
      recordLookupEvent(result.source || "upstream");
      return result;
    }
  } catch (_error) {
    metrics.upstreamFailures += 1;
    upstreamUnavailable = true;
    logger?.error("ca_upstream_unavailable", { ca: caNumber });
  }

  const fallback = localFallback[caNumber];
  metrics.fallbackServed += 1;
  if (!fallback) {
    logger?.warn("ca_fallback_not_found", {
      ca: caNumber,
      status_reason: upstreamUnavailable ? "upstream_unavailable" : "not_found"
    });
    const result = writeCache(caNumber, normalizeCaPayload({
      ca: caNumber,
      is_valid: false,
      item_name: "Nao encontrado",
      risk_coverage: "-",
      valid_until: "-",
      source: "fallback",
      status_reason: upstreamUnavailable ? "upstream_unavailable" : "not_found"
    }), logger);
    recordLookupEvent(result.source || "fallback");
    return result;
  }

  logger?.info("ca_fallback_local_served", {
    ca: caNumber,
    status_reason: fallback.status_reason ?? (upstreamUnavailable ? "upstream_unavailable" : null)
  });
  const result = writeCache(caNumber, normalizeCaPayload({
    ...fallback,
    status_reason: fallback.status_reason ?? (upstreamUnavailable ? "upstream_unavailable" : null)
  }), logger);
  recordLookupEvent(result.source || "fallback-local");
  return result;
}

module.exports = {
  lookupCa,
  getCaMetrics,
  getHomeMetrics,
  getCaConfig,
  setCaProviderLogger,
  clearCaCache,
  warmupCaEntries,
  resetCaState
};

