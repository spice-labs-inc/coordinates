// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

// Zero-dependency structural check for the intrinsic vectors. Mirrors vectors.schema.json
// (kept as the human-readable format spec) without pulling a JSON Schema validator into CI.
//   node vectors/validate.mjs
import { readFileSync } from "node:fs";

const IDS = new Set(["md5", "sha1", "sha256", "sha512", "gitoid-blob-sha1", "gitoid-blob-sha256"]);
const HEX = /^([0-9a-f]{2})*$/;

const doc = JSON.parse(readFileSync(new URL("./intrinsic.json", import.meta.url), "utf8"));
const errors = [];

if (!Array.isArray(doc.vectors) || doc.vectors.length === 0) {
  errors.push("`vectors` must be a non-empty array");
}
for (const [i, v] of (doc.vectors ?? []).entries()) {
  const at = `vectors[${i}]${v && v.name ? ` (${v.name})` : ""}`;
  if (typeof v.name !== "string") errors.push(`${at}: name must be a string`);
  if (typeof v.input_hex !== "string" || !HEX.test(v.input_hex)) {
    errors.push(`${at}: input_hex must be lowercase hex`);
  }
  if (typeof v.expect !== "object" || v.expect === null || Object.keys(v.expect).length === 0) {
    errors.push(`${at}: expect must be a non-empty object`);
  } else {
    for (const [id, val] of Object.entries(v.expect)) {
      if (!IDS.has(id)) errors.push(`${at}: unknown identifier "${id}"`);
      if (typeof val !== "string") errors.push(`${at}: ${id} must be a string`);
    }
  }
}

if (errors.length) {
  console.error("intrinsic.json invalid:\n" + errors.map((e) => "  - " + e).join("\n"));
  process.exit(1);
}
console.log(`intrinsic.json valid — ${doc.vectors.length} vectors`);
