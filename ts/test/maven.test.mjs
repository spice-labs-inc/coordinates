// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

// Tests for purl.toMavenUrl, which leaves `+` unencoded in Maven versions.

import { test } from "node:test";
import assert from "node:assert/strict";
import { purl } from "../dist/index.js";

test("maven version with + is literal", () => {
  assert.equal(
    purl.toMavenUrl(purl.parse("pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7")),
    "pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7"
  );
});

test("maven version with + and qualifier", () => {
  assert.equal(
    purl.toMavenUrl(
      purl.parse("pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7?classifier=sources")
    ),
    "pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7?classifier=sources"
  );
});

test("maven version without + is unchanged", () => {
  assert.equal(
    purl.toMavenUrl(purl.parse("pkg:maven/org.apache.commons/io@1.0.0")),
    "pkg:maven/org.apache.commons/io@1.0.0"
  );
});

test("non-maven version with + is still encoded", () => {
  assert.equal(
    purl.toMavenUrl(purl.parse("pkg:deb/debian/attr@1:2.4.47-2+b1")),
    "pkg:deb/debian/attr@1:2.4.47-2%2Bb1"
  );
});

test("maven name with + is still encoded", () => {
  assert.equal(
    purl.toMavenUrl(purl.parse("pkg:maven/g/art+ifact@1.0")),
    "pkg:maven/g/art%2Bifact@1.0"
  );
});

test("maven qualifier value with + is still encoded", () => {
  assert.equal(
    purl.toMavenUrl(purl.parse("pkg:maven/g/a@1.0?foo=a+b")),
    "pkg:maven/g/a@1.0?foo=a%2Bb"
  );
});

test("maven version with other unsafe characters still encodes", () => {
  assert.equal(purl.toMavenUrl(purl.parse("pkg:maven/g/a@1.0 0")), "pkg:maven/g/a@1.0%200");
});

test("maven version with + roundtrips", () => {
  const original = purl.parse("pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7");
  const roundtripped = purl.parse(purl.toMavenUrl(original));
  assert.equal(original.version, roundtripped.version);
});
