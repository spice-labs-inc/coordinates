// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

package io.spicelabs.coordinates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Runs the shared vectors in ../../vectors/intrinsic.json against this implementation. */
class ConformanceTest {

  @TestFactory
  List<DynamicTest> intrinsicVectors() throws Exception {
    String text = new String(Files.readAllBytes(vectorsFile()), StandardCharsets.UTF_8);
    JsonObject doc = JsonParser.parseString(text).getAsJsonObject();

    List<DynamicTest> tests = new ArrayList<DynamicTest>();
    for (JsonElement entry : doc.getAsJsonArray("vectors")) {
      JsonObject vector = entry.getAsJsonObject();
      final String name = vector.get("name").getAsString();
      final byte[] input = fromHex(vector.get("input_hex").getAsString());
      final JsonObject expect = vector.getAsJsonObject("expect");
      tests.add(
          dynamicTest(
              "intrinsic: " + name,
              () -> {
                Map<String, String> got = Coordinates.intrinsic(input);
                for (Map.Entry<String, JsonElement> e : expect.entrySet()) {
                  assertEquals(
                      e.getValue().getAsString(),
                      got.get(e.getKey()),
                      e.getKey() + " for input \"" + name + "\"");
                }
              }));
    }
    return tests;
  }

  // Same vectors, but driven through the streaming IntrinsicHasher one byte at a time (worst-case
  // chunk boundaries) and through intrinsic(InputStream, long) — both must match the spec exactly.
  @TestFactory
  List<DynamicTest> intrinsicStreamingVectors() throws Exception {
    String text = new String(Files.readAllBytes(vectorsFile()), StandardCharsets.UTF_8);
    JsonObject doc = JsonParser.parseString(text).getAsJsonObject();

    List<DynamicTest> tests = new ArrayList<DynamicTest>();
    for (JsonElement entry : doc.getAsJsonArray("vectors")) {
      JsonObject vector = entry.getAsJsonObject();
      final String name = vector.get("name").getAsString();
      final byte[] input = fromHex(vector.get("input_hex").getAsString());
      final JsonObject expect = vector.getAsJsonObject("expect");
      tests.add(
          dynamicTest(
              "intrinsic streaming: " + name,
              () -> {
                Coordinates.IntrinsicHasher hasher = new Coordinates.IntrinsicHasher(input.length);
                for (byte b : input) {
                  hasher.update(new byte[] {b});
                }
                Map<String, String> streamed = hasher.finish();
                Map<String, String> read =
                    Coordinates.intrinsic(new ByteArrayInputStream(input), input.length);
                for (Map.Entry<String, JsonElement> e : expect.entrySet()) {
                  String expected = e.getValue().getAsString();
                  assertEquals(
                      expected, streamed.get(e.getKey()), "streamed " + e.getKey() + ": " + name);
                  assertEquals(
                      expected, read.get(e.getKey()), "reader " + e.getKey() + ": " + name);
                }
              }));
    }
    return tests;
  }

  private static Path vectorsFile() {
    Path[] candidates = {
      Paths.get("..", "vectors", "intrinsic.json"), Paths.get("vectors", "intrinsic.json"),
    };
    for (Path p : candidates) {
      if (Files.exists(p)) {
        return p;
      }
    }
    throw new IllegalStateException(
        "vectors/intrinsic.json not found from " + Paths.get(".").toAbsolutePath());
  }

  private static byte[] fromHex(String hex) {
    byte[] out = new byte[hex.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }
}
