// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

// Runs the shared vectors in ../../vectors/intrinsic.json against the built implementation.
// Plain JS on purpose: no test-runner dependency, and it exercises exactly what gets published.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { intrinsic, IntrinsicHasher, intrinsicStream } from "../dist/index.js";

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

// Same vectors, driven through the streaming API one byte at a time (worst-case chunk boundaries)
// via IntrinsicHasher and intrinsicStream — both must match the spec exactly.
for (const vector of suite.vectors) {
  test(`intrinsic streaming: ${vector.name}`, async () => {
    const input = Buffer.from(vector.input_hex, "hex");

    const hasher = new IntrinsicHasher();
    for (const byte of input) hasher.update(Uint8Array.of(byte));
    const streamed = await hasher.finish();

    async function* oneByteAtATime() {
      for (const byte of input) yield Uint8Array.of(byte);
    }
    const fromStream = await intrinsicStream(oneByteAtATime());

    for (const [id, expected] of Object.entries(vector.expect)) {
      assert.equal(streamed[id], expected, `streamed ${id} for input "${vector.name}"`);
      assert.equal(fromStream[id], expected, `stream ${id} for input "${vector.name}"`);
    }
  });
}
