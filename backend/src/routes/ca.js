const express = require("express");
const { lookupCa } = require("../services/caProvider");
const { validateCaPayload } = require("../validators/caSchema");

const router = express.Router();

router.get("/ca/:numero", async (req, res) => {
  const caNumber = String(req.params.numero || "").trim();
  if (!/^\d{3,}$/.test(caNumber)) {
    req.logger?.warn("invalid_ca_number", { ca: caNumber || null });
    return res.status(400).json({
      error: "invalid_ca_number",
      message: "Use apenas digitos e no minimo 3 caracteres.",
      request_id: req.requestId
    });
  }

  const payload = await lookupCa(caNumber, req.logger?.child({ ca: caNumber }));
  const validation = validateCaPayload(payload);
  if (!validation.ok) {
    req.logger?.error("invalid_upstream_payload", {
      ca: caNumber,
      details: validation.errors
    });
    return res.status(502).json({
      error: "invalid_upstream_payload",
      details: validation.errors,
      request_id: req.requestId
    });
  }

  return res.json({
    ...validation.value,
    request_id: req.requestId
  });
});

module.exports = router;

