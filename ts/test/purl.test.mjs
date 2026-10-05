// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

// Runs the vendored purl-spec suites against this implementation:
//   ../../vectors/extrinsic.json   — the base spec suite
//   ../../vectors/purl-types.json  — per-type normalization + validation
//   ../../vectors/purl-ns-rules.json — per-type namespace required/prohibited rules
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { purl } from "../dist/index.js";

const SUITES = [
  "../../vectors/extrinsic.json",
  "../../vectors/purl-types.json",
  "../../vectors/purl-ns-rules.json"
];

const nullIfEmpty = (q) => (q && Object.keys(q).length ? q : null);

function got(p) {
  return {
    type: p.type,
    namespace: p.namespace ?? null,
    name: p.name,
    version: p.version ?? null,
    qualifiers: nullIfEmpty(p.qualifiers),
    subpath: p.subpath ?? null
  };
}

function want(o) {
  return {
    type: o.type ?? null,
    namespace: o.namespace ?? null,
    name: o.name ?? null,
    version: o.version ?? null,
    qualifiers: nullIfEmpty(o.qualifiers),
    subpath: o.subpath ?? null
  };
}

for (const file of SUITES) {
  const label = file.split("/").pop();
  const suite = JSON.parse(readFileSync(new URL(file, import.meta.url), "utf8"));
  for (const [i, c] of suite.tests.entries()) {
    test(`${label} ${c.test_type} [${i}]: ${c.description}`, () => {
      if (c.test_type === "parse") {
        if (c.expected_failure) assert.throws(() => purl.parse(c.input));
        else assert.deepEqual(got(purl.parse(c.input)), want(c.expected_output));
      } else if (c.test_type === "build") {
        const input = { ...c.input, qualifiers: c.input.qualifiers ?? {} };
        if (c.expected_failure) assert.throws(() => purl.build(input));
        else assert.equal(purl.build(input), c.expected_output);
      } else if (c.test_type === "roundtrip") {
        if (c.expected_failure) assert.throws(() => purl.build(purl.parse(c.input)));
        else assert.equal(purl.build(purl.parse(c.input)), c.expected_output);
      }
    });
  }
}

// ../../vectors/purl-unknown-ns.json — the unknown-namespace sentinel, lenient path, and refines.
const unknownNs = JSON.parse(
  readFileSync(new URL("../../vectors/purl-unknown-ns.json", import.meta.url), "utf8")
);
for (const [i, c] of unknownNs.tests.entries()) {
  test(`purl-unknown-ns.json ${c.test_type} [${i}]: ${c.description}`, () => {
    const options = { missingNamespace: c.missing_namespace ?? "reject" };
    const run = {
      parse: () => got(purl.parse(c.input, options)),
      build: () => purl.build({ ...c.input, qualifiers: c.input.qualifiers ?? {} }, options),
      roundtrip: () => purl.build(purl.parse(c.input, options)),
      is_namespace_unknown: () => purl.isNamespaceUnknown(purl.parse(c.input)),
      refines: () => purl.refines(purl.parse(c.input.known), purl.parse(c.input.partial))
    }[c.test_type];
    assert.ok(run, `unknown test_type: ${c.test_type}`);
    if (c.expected_failure) assert.throws(run);
    else if (c.test_type === "parse") assert.deepEqual(run(), want(c.expected_output));
    else assert.equal(run(), c.expected_output);
  });
}
