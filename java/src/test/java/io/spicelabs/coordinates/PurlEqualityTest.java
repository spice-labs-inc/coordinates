// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

package io.spicelabs.coordinates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link Purl} equality is by canonical form, so it ignores case folding and qualifier order. */
class PurlEqualityTest {

  @Test
  void equalWhenCanonicalFormsMatch() {
    Purl parsed = Purl.parse("pkg:composer/Foo/Bar@1.0");
    Purl built = new Purl("composer", "foo", "bar", "1.0", null, null);
    assertEquals(parsed, built);
    assertEquals(parsed.hashCode(), built.hashCode());
  }

  @Test
  void qualifierOrderDoesNotMatter() {
    Map<String, String> ab = new LinkedHashMap<String, String>();
    ab.put("a", "1");
    ab.put("b", "2");
    Map<String, String> ba = new LinkedHashMap<String, String>();
    ba.put("b", "2");
    ba.put("a", "1");
    assertEquals(
        new Purl("npm", null, "x", "1", ab, null), new Purl("npm", null, "x", "1", ba, null));
  }

  @Test
  void differentPurlsAreNotEqual() {
    assertNotEquals(
        Purl.parse("pkg:cpan/ETHER/Moose@2.2207"),
        Purl.parse("pkg:cpan/Moose@2.2207", Purl.MissingNamespace.UNKNOWN));
  }

  @Test
  void deduplicatesInHashSet() {
    Set<Purl> set = new HashSet<Purl>();
    set.add(Purl.parse("pkg:cpan/Moose@2.2207", Purl.MissingNamespace.UNKNOWN));
    set.add(Purl.parse("pkg:cpan/~unknown/Moose@2.2207"));
    assertEquals(1, set.size());
  }

  @Test
  void invalidPurlsCompareByFieldsWithoutThrowing() {
    Purl a = new Purl("cpan", null, "Moose", "2.2207", null, null);
    Purl b = new Purl("cpan", null, "Moose", "2.2207", null, null);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, Purl.parse("pkg:cpan/~unknown/Moose@2.2207"));
  }
}
