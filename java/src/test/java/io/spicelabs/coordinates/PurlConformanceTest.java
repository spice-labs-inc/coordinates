// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

package io.spicelabs.coordinates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the vendored purl-spec suite in ../../vectors/extrinsic.json against this implementation.
 */
class PurlConformanceTest {

  @TestFactory
  List<DynamicTest> purlVectors() throws Exception {
    List<DynamicTest> tests = new ArrayList<DynamicTest>();
    for (String suite : new String[] {"extrinsic.json", "purl-types.json", "purl-ns-rules.json"}) {
      String text = new String(Files.readAllBytes(vectorFile(suite)), StandardCharsets.UTF_8);
      JsonObject doc = JsonParser.parseString(text).getAsJsonObject();
      for (JsonElement entry : doc.getAsJsonArray("tests")) {
        final JsonObject c = entry.getAsJsonObject();
        final String type = c.get("test_type").getAsString();
        final boolean fail = c.has("expected_failure") && c.get("expected_failure").getAsBoolean();
        final JsonElement input = c.get("input");
        final JsonElement expected = c.get("expected_output");
        final String desc = c.get("description").getAsString();

        tests.add(
            dynamicTest(
                suite + " " + type + ": " + desc,
                () -> {
                  if ("parse".equals(type)) {
                    if (fail) {
                      assertThrows(Purl.PurlException.class, () -> Purl.parse(input.getAsString()));
                    } else {
                      assertComponents(
                          Purl.parse(input.getAsString()), expected.getAsJsonObject(), desc);
                    }
                  } else if ("build".equals(type)) {
                    if (fail) {
                      assertThrows(
                          Purl.PurlException.class,
                          () -> fromComponents(input.getAsJsonObject()).toCanonical());
                    } else {
                      assertEquals(
                          expected.getAsString(),
                          fromComponents(input.getAsJsonObject()).toCanonical());
                    }
                  } else if ("roundtrip".equals(type)) {
                    if (fail) {
                      assertThrows(
                          Purl.PurlException.class,
                          () -> Purl.parse(input.getAsString()).toCanonical());
                    } else {
                      assertEquals(
                          expected.getAsString(), Purl.parse(input.getAsString()).toCanonical());
                    }
                  }
                }));
      }
    }
    return tests;
  }

  private static Purl fromComponents(JsonObject c) {
    Map<String, String> qualifiers = new LinkedHashMap<String, String>();
    JsonElement q = c.get("qualifiers");
    if (q != null && q.isJsonObject()) {
      for (Map.Entry<String, JsonElement> e : q.getAsJsonObject().entrySet()) {
        qualifiers.put(e.getKey(), e.getValue().getAsString());
      }
    }
    return new Purl(
        str(c, "type"),
        str(c, "namespace"),
        str(c, "name"),
        str(c, "version"),
        qualifiers,
        str(c, "subpath"));
  }

  private static void assertComponents(Purl p, JsonObject expected, String desc) {
    assertEquals(str(expected, "type"), p.type, "type: " + desc);
    assertEquals(str(expected, "namespace"), p.namespace, "namespace: " + desc);
    assertEquals(str(expected, "name"), p.name, "name: " + desc);
    assertEquals(str(expected, "version"), p.version, "version: " + desc);
    assertEquals(
        qualifiers(expected.get("qualifiers")), normalize(p.qualifiers), "qualifiers: " + desc);
    assertEquals(str(expected, "subpath"), p.subpath, "subpath: " + desc);
  }

  private static String str(JsonObject o, String key) {
    JsonElement e = o.get(key);
    return (e == null || e.isJsonNull()) ? null : e.getAsString();
  }

  private static Map<String, String> qualifiers(JsonElement q) {
    if (q == null || q.isJsonNull()) {
      return null;
    }
    Map<String, String> r = new LinkedHashMap<String, String>();
    for (Map.Entry<String, JsonElement> e : q.getAsJsonObject().entrySet()) {
      r.put(e.getKey(), e.getValue().getAsString());
    }
    return r.isEmpty() ? null : r;
  }

  private static Map<String, String> normalize(Map<String, String> q) {
    return (q == null || q.isEmpty()) ? null : q;
  }

  private static Path vectorFile(String name) {
    Path[] candidates = {Paths.get("..", "vectors", name), Paths.get("vectors", name)};
    for (Path p : candidates) {
      if (Files.exists(p)) {
        return p;
      }
    }
    throw new IllegalStateException("vectors/" + name + " not found");
  }
}
