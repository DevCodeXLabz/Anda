const LEVELS = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40
};

function resolveLevel(level) {
  return LEVELS[level] ?? LEVELS.info;
}

function createLogger(options = {}) {
  const service = options.service || "anda-ca-backend";
  const configuredLevel = String(options.level || process.env.LOG_LEVEL || "info").toLowerCase();
  const currentLevel = resolveLevel(configuredLevel);
  const baseContext = options.baseContext || {};
  const sink = options.sink || ((line, level) => {
    if (level === "error") {
      console.error(line);
      return;
    }

    console.log(line);
  });

  function emit(level, message, context = {}) {
    if (resolveLevel(level) < currentLevel) {
      return;
    }

    const payload = {
      timestamp: new Date().toISOString(),
      level,
      service,
      message,
      ...baseContext,
      ...context
    };

    sink(JSON.stringify(payload), level, payload);
  }

  return {
    debug(message, context) {
      emit("debug", message, context);
    },
    info(message, context) {
      emit("info", message, context);
    },
    warn(message, context) {
      emit("warn", message, context);
    },
    error(message, context) {
      emit("error", message, context);
    },
    child(context = {}) {
      return createLogger({
        service,
        level: configuredLevel,
        sink,
        baseContext: {
          ...baseContext,
          ...context
        }
      });
    }
  };
}

module.exports = {
  createLogger
};

