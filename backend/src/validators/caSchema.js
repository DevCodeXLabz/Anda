function isNonEmptyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function normalizeBoolean(value) {
  if (typeof value === "boolean") return value;
  if (typeof value === "number") return value !== 0;
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (["true", "1", "ok", "valido", "válido", "ativo", "active"].includes(normalized)) {
      return true;
    }
    if (["false", "0", "invalido", "inválido", "vencido", "expired", "inactive"].includes(normalized)) {
      return false;
    }
  }

  return null;
}

function normalizeCaPayload(payload) {
  const source = isNonEmptyString(payload.source) ? payload.source.trim() : "oficial";

  return {
    ca: String(payload.ca ?? "").trim(),
    is_valid: normalizeBoolean(payload.is_valid),
    item_name: isNonEmptyString(payload.item_name) ? payload.item_name.trim() : "-",
    risk_coverage: isNonEmptyString(payload.risk_coverage) ? payload.risk_coverage.trim() : "-",
    valid_until: isNonEmptyString(payload.valid_until) ? payload.valid_until.trim() : "-",
    source,
    cached: payload.cached === true,
    status_reason: isNonEmptyString(payload.status_reason) ? payload.status_reason.trim() : null
  };
}

function validateCaPayload(payload) {
  const normalized = normalizeCaPayload(payload);
  const errors = [];

  if (!/^\d{3,}$/.test(normalized.ca)) {
    errors.push("Field 'ca' must contain at least 3 digits.");
  }

  if (typeof normalized.is_valid !== "boolean") {
    errors.push("Field 'is_valid' must be boolean.");
  }

  return {
    ok: errors.length === 0,
    errors,
    value: normalized
  };
}

module.exports = {
  validateCaPayload,
  normalizeCaPayload
};

