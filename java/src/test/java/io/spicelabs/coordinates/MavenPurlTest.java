// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

package io.spicelabs.coordinates;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for {@link Purl#toMavenUrl()}, which leaves {@code +} unencoded in Maven versions. */
class MavenPurlTest {

  @Test
  void mavenVersionWithPlusIsLiteral() {
    assertEquals(
        "pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7",
        Purl.parse("pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7").toMavenUrl());
  }

  @Test
  void mavenVersionWithPlusAndQualifier() {
    assertEquals(
        "pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7?classifier=sources",
        Purl.parse("pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7?classifier=sources")
            .toMavenUrl());
  }

  @Test
  void mavenVersionWithoutPlusIsUnchanged() {
    assertEquals(
        "pkg:maven/org.apache.commons/io@1.0.0",
        Purl.parse("pkg:maven/org.apache.commons/io@1.0.0").toMavenUrl());
  }

  @Test
  void nonMavenVersionWithPlusIsStillEncoded() {
    assertEquals(
        "pkg:deb/debian/attr@1:2.4.47-2%2Bb1",
        Purl.parse("pkg:deb/debian/attr@1:2.4.47-2+b1").toMavenUrl());
  }

  @Test
  void mavenNameWithPlusIsStillEncoded() {
    assertEquals(
        "pkg:maven/g/art%2Bifact@1.0", Purl.parse("pkg:maven/g/art+ifact@1.0").toMavenUrl());
  }

  @Test
  void mavenQualifierValueWithPlusIsStillEncoded() {
    assertEquals(
        "pkg:maven/g/a@1.0?foo=a%2Bb", Purl.parse("pkg:maven/g/a@1.0?foo=a+b").toMavenUrl());
  }

  @Test
  void mavenVersionWithOtherUnsafeCharactersStillEncodes() {
    assertEquals("pkg:maven/g/a@1.0%200", Purl.parse("pkg:maven/g/a@1.0 0").toMavenUrl());
  }

  @Test
  void mavenVersionWithPlusRoundtrips() {
    Purl original = Purl.parse("pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7");
    assertEquals(original.version, Purl.parse(original.toMavenUrl()).version);
  }
}
