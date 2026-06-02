// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

// Runs the shared vectors in ../../vectors/intrinsic.json against the built implementation.
// Plain JS on purpose: no test-runner dependency, and it exercises exactly what gets published.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { intrinsic } from "../dist/index.js";

const suite = JSON.parse(
  readFileSync(new URL("../../vectors/intrinsic.json", import.meta.url), "utf8")
);

for (const vector of suite.vectors) {
  test(`intrinsic: ${vector.name}`, async () => {
    const input = Buffer.from(vector.input_hex, "hex");
    const got = await intrinsic(input);
    for (const [id, expected] of Object.entries(vector.expect)) {
      assert.equal(got[id], expected, `${id} for input "${vector.name}"`);
    }
  });
}
