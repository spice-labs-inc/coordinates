// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

package io.spicelabs.coordinates;

import java.io.IOException;
import java.io.InputStream;
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
    return new IntrinsicHasher(input.length).update(input).finish();
  }

  /**
   * Every intrinsic identifier for the {@code contentLength} bytes read from {@code in}, in a
   * single pass — the streaming counterpart to {@link #intrinsic(byte[])}, for hashing a file or
   * socket without buffering it all. {@code contentLength} must be the exact number of bytes {@code
   * in} yields (e.g. a file's size); gitoid framing depends on it. Does not close {@code in}.
   */
  public static Map<String, String> intrinsic(InputStream in, long contentLength)
      throws IOException {
    IntrinsicHasher hasher = new IntrinsicHasher(contentLength);
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) {
      hasher.update(buf, 0, n);
    }
    return hasher.finish();
  }

  /**
   * Streaming computation of every intrinsic identifier in a single pass over the input.
   *
   * <p>The plain digests would stream with no length known in advance, but {@code gitoid} framing
   * embeds the total content length ({@code "blob " + len + NUL + content}, see ../../spec.yaml),
   * which must be digested before any content — so the length is required up front, in the
   * constructor. Feed the bytes with {@link #update(byte[])} (any chunking) and read the
   * identifiers with {@link #finish()}; the result matches {@link Coordinates#intrinsic(byte[])}
   * exactly. Not thread-safe.
   */
  public static final class IntrinsicHasher {
    private final MessageDigest md5;
    private final MessageDigest sha1;
    private final MessageDigest sha256;
    private final MessageDigest sha512;
    private final MessageDigest gitoidSha1;
    private final MessageDigest gitoidSha256;

    /**
     * A hasher for content of exactly {@code contentLength} bytes. The two gitoid digests are
     * seeded with the git blob header for that length; the plain digests start empty.
     */
    public IntrinsicHasher(long contentLength) {
      md5 = newDigest("MD5");
      sha1 = newDigest("SHA-1");
      sha256 = newDigest("SHA-256");
      sha512 = newDigest("SHA-512");
      gitoidSha1 = newDigest("SHA-1");
      gitoidSha256 = newDigest("SHA-256");
      byte[] header = blobHeader(contentLength);
      gitoidSha1.update(header);
      gitoidSha256.update(header);
    }

    /** Feed the next chunk of content to every digest. */
    public IntrinsicHasher update(byte[] chunk) {
      return update(chunk, 0, chunk.length);
    }

    /**
     * Feed {@code len} bytes of {@code chunk} starting at {@code off} to every digest. Call zero or
     * more times; the chunks concatenated must total the {@code contentLength} given to the
     * constructor.
     */
    public IntrinsicHasher update(byte[] chunk, int off, int len) {
      md5.update(chunk, off, len);
      sha1.update(chunk, off, len);
      sha256.update(chunk, off, len);
      sha512.update(chunk, off, len);
      gitoidSha1.update(chunk, off, len);
      gitoidSha256.update(chunk, off, len);
      return this;
    }

    /**
     * Finalize and return every intrinsic identifier, keyed by its spec.yaml name — the same shape
     * and order as {@link Coordinates#intrinsic(byte[])}.
     */
    public Map<String, String> finish() {
      Map<String, String> out = new LinkedHashMap<String, String>();
      out.put("md5", hex(md5.digest()));
      out.put("sha1", hex(sha1.digest()));
      out.put("sha256", hex(sha256.digest()));
      out.put("sha512", hex(sha512.digest()));
      out.put("gitoid-blob-sha1", "gitoid:blob:sha1:" + hex(gitoidSha1.digest()));
      out.put("gitoid-blob-sha256", "gitoid:blob:sha256:" + hex(gitoidSha256.digest()));
      return out;
    }
  }

  /**
   * The git loose-object header for a blob of {@code len} bytes: ASCII "blob &lt;len&gt;" then a
   * NUL.
   */
  private static byte[] blobHeader(long len) {
    byte[] prefix = ("blob " + len).getBytes(StandardCharsets.US_ASCII);
    byte[] header = new byte[prefix.length + 1];
    System.arraycopy(prefix, 0, header, 0, prefix.length);
    header[prefix.length] = 0; // single NUL byte
    return header;
  }

  /** Lowercase-hex digest of the parts, fed to one digest run in order. */
  private static byte[] digest(String algorithm, byte[]... parts) {
    MessageDigest md = newDigest(algorithm);
    for (byte[] part : parts) {
      md.update(part);
    }
    return md.digest();
  }

  private static MessageDigest newDigest(String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
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
