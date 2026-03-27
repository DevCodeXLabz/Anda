const test = require("node:test");
const assert = require("node:assert/strict");
const { validateCaPayload } = require("../src/validators/caSchema");

test("validateCaPayload rejects invalid boolean-like values", () => {
  const result = validateCaPayload({
    ca: "12345",
    is_valid: "talvez",
    item_name: "Capacete",
    risk_coverage: "Impacto",
    valid_until: "2030-01-01",
    source: "upstream"
  });

  assert.equal(result.ok, false);
  assert.match(result.errors[0], /is_valid/);
});

test("validateCaPayload accepts normalized string booleans", () => {
  const result = validateCaPayload({
    ca: "12345",
    is_valid: "valido",
    item_name: "Capacete",
    risk_coverage: "Impacto",
    valid_until: "2030-01-01",
    source: "upstream"
  });

  assert.equal(result.ok, true);
  assert.equal(result.value.is_valid, true);
});

