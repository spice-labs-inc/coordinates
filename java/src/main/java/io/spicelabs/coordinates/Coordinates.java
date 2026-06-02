// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

package io.spicelabs.coordinates;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Intrinsic content identifiers, per ../../spec.yaml. JDK-only ({@link MessageDigest}), no
 * dependencies, Java 8 compatible.
 *
 * <p>A {@code gitoid:blob:<name>} is a git blob object id: the digest of {@code "blob " + len + NUL
 * + content}, the exact value {@code git hash-object} produces. A plain digest is the lowercase hex
 * of the digest of the content.
 */
public final class Coordinates {

  private Coordinates() {}

  public static String md5(byte[] input) {
    return hex(digest("MD5", input));
  }

  public static String sha1(byte[] input) {
    return hex(digest("SHA-1", input));
  }

  public static String sha256(byte[] input) {
    return hex(digest("SHA-256", input));
  }

  public static String sha512(byte[] input) {
    return hex(digest("SHA-512", input));
  }

  public static String gitoidBlobSha1(byte[] input) {
    return "gitoid:blob:sha1:" + hex(digest("SHA-1", blobHeader(input.length), input));
  }

  public static String gitoidBlobSha256(byte[] input) {
    return "gitoid:blob:sha256:" + hex(digest("SHA-256", blobHeader(input.length), input));
  }

  /** Every intrinsic identifier for {@code input}, keyed by its spec.yaml name. */
  public static Map<String, String> intrinsic(byte[] input) {
    Map<String, String> out = new LinkedHashMap<String, String>();
    out.put("md5", md5(input));
    out.put("sha1", sha1(input));
    out.put("sha256", sha256(input));
    out.put("sha512", sha512(input));
    out.put("gitoid-blob-sha1", gitoidBlobSha1(input));
    out.put("gitoid-blob-sha256", gitoidBlobSha256(input));
    return out;
  }

  /**
   * The git loose-object header for a blob of {@code len} bytes: ASCII "blob &lt;len&gt;" then a
   * NUL.
   */
  private static byte[] blobHeader(int len) {
    byte[] prefix = ("blob " + len).getBytes(StandardCharsets.US_ASCII);
    byte[] header = new byte[prefix.length + 1];
    System.arraycopy(prefix, 0, header, 0, prefix.length);
    header[prefix.length] = 0; // single NUL byte
    return header;
  }

  /** Lowercase-hex digest of the parts, fed to one digest run in order. */
  private static byte[] digest(String algorithm, byte[]... parts) {
    try {
      MessageDigest md = MessageDigest.getInstance(algorithm);
      for (byte[] part : parts) {
        md.update(part);
      }
      return md.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(algorithm + " not available", e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >> 4) & 0xF, 16));
      sb.append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }
}
