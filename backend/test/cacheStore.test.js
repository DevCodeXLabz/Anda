const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { loadCacheSnapshot, saveCacheSnapshot } = require("../src/services/cacheStore");

test("cacheStore saves and loads valid cache entries", async () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "anda-cache-"));
  const filePath = path.join(tempDir, "cache.json");

  const cacheMap = new Map();
  cacheMap.set("12345", {
    payload: { ca: "12345", is_valid: true, source: "fallback-local" },
    expiresAt: Date.now() + 60_000
  });

  await saveCacheSnapshot(filePath, cacheMap);
  const loaded = loadCacheSnapshot(filePath);

  assert.equal(loaded.size, 1);
  assert.equal(loaded.get("12345").payload.ca, "12345");
});

test("cacheStore ignores expired entries while loading", async () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "anda-cache-"));
  const filePath = path.join(tempDir, "cache.json");

  const expiredPayload = {
    savedAt: new Date().toISOString(),
    entries: [
      {
        ca: "98765",
        payload: { ca: "98765", is_valid: false, source: "fallback" },
        expiresAt: Date.now() - 10_000
      }
    ]
  };

  fs.writeFileSync(filePath, JSON.stringify(expiredPayload), "utf8");
  const loaded = loadCacheSnapshot(filePath);

  assert.equal(loaded.size, 0);
});

