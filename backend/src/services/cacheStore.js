const fs = require("fs");
const path = require("path");

function loadCacheSnapshot(filePath, now = Date.now()) {
  try {
    if (!fs.existsSync(filePath)) {
      return new Map();
    }

    const raw = fs.readFileSync(filePath, "utf8");
    if (!raw.trim()) {
      return new Map();
    }

    const parsed = JSON.parse(raw);
    const entries = Array.isArray(parsed.entries) ? parsed.entries : [];
    const restored = new Map();

    for (const entry of entries) {
      if (!entry || typeof entry.ca !== "string") continue;
      if (typeof entry.expiresAt !== "number" || entry.expiresAt <= now) continue;

      restored.set(entry.ca, {
        payload: entry.payload || {},
        expiresAt: entry.expiresAt
      });
    }

    return restored;
  } catch (_error) {
    return new Map();
  }
}

async function saveCacheSnapshot(filePath, cacheMap) {
  const entries = [];

  for (const [ca, value] of cacheMap.entries()) {
    entries.push({
      ca,
      payload: value.payload,
      expiresAt: value.expiresAt
    });
  }

  const payload = JSON.stringify(
    {
      savedAt: new Date().toISOString(),
      entries
    },
    null,
    2
  );

  const directory = path.dirname(filePath);
  await fs.promises.mkdir(directory, { recursive: true });

  const tempPath = `${filePath}.tmp`;
  await fs.promises.writeFile(tempPath, payload, "utf8");
  await fs.promises.rename(tempPath, filePath);
}

module.exports = {
  loadCacheSnapshot,
  saveCacheSnapshot
};

