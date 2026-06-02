// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

/**
 * Intrinsic content identifiers, per ../../spec.yaml. Isomorphic: runs in Node and the browser on
 * the Web Crypto API. MD5 — which Web Crypto does not provide — is the one vendored algorithm.
 *
 * A `gitoid:blob:<name>` is a git blob object id: the digest of `"blob " + len + NUL + content`, the
 * exact value `git hash-object` produces. A plain digest is the lowercase hex of the digest of the
 * content. See ../../spec.yaml for the definitions and ../../vectors for the proof.
 *
 * The API is async because the Web Crypto digest is async (there is no synchronous hashing in the
 * browser).
 */

// Extrinsic identifiers — purl (package URL).
export * as purl from "./purl.js";

const ASCII = new TextEncoder();

function toHex(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += b.toString(16).padStart(2, "0");
  return s;
}

function concat(parts: Uint8Array[]) {
  let total = 0;
  for (const p of parts) total += p.length;
  const out = new Uint8Array(total);
  let offset = 0;
  for (const p of parts) {
    out.set(p, offset);
    offset += p.length;
  }
  return out;
}

/** The git loose-object header for a blob of `len` bytes: ASCII "blob <len>" then a single NUL. */
function blobHeader(len: number): Uint8Array {
  return Uint8Array.from([...ASCII.encode(`blob ${len}`), 0x00]);
}

async function subtleHex(
  algorithm: "SHA-1" | "SHA-256" | "SHA-512",
  ...parts: Uint8Array[]
): Promise<string> {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) {
    throw new Error(
      "Web Crypto is unavailable: SHA hashing needs a secure context (https or localhost) in the " +
        "browser, or Node 20+. MD5 works without it."
    );
  }
  // Hash the single view directly when there is nothing to join — avoids copying the whole input.
  // The cast bridges TS 5.9's Uint8Array<ArrayBufferLike> vs BufferSource; a Uint8Array is always a
  // valid BufferSource at runtime.
  const data = (parts.length === 1 ? parts[0] : concat(parts)) as BufferSource;
  const digest = await subtle.digest(algorithm, data);
  return toHex(new Uint8Array(digest));
}

export const sha1 = (input: Uint8Array): Promise<string> => subtleHex("SHA-1", input);
export const sha256 = (input: Uint8Array): Promise<string> => subtleHex("SHA-256", input);
export const sha512 = (input: Uint8Array): Promise<string> => subtleHex("SHA-512", input);

export const gitoidBlobSha1 = async (input: Uint8Array): Promise<string> =>
  `gitoid:blob:sha1:${await subtleHex("SHA-1", blobHeader(input.length), input)}`;

export const gitoidBlobSha256 = async (input: Uint8Array): Promise<string> =>
  `gitoid:blob:sha256:${await subtleHex("SHA-256", blobHeader(input.length), input)}`;

// MD5 (implemented from RFC 1321 section 3.4 pseudocode) — vendored, because Web Crypto does not
// implement it. A content alias for lookup, not a security primitive. Proven by ../../vectors.
const MD5_S = [
  7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14,
  20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21, 6,
  10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
];
const MD5_K = (() => {
  const k = new Int32Array(64);
  for (let i = 0; i < 64; i++) k[i] = Math.floor(Math.abs(Math.sin(i + 1)) * 0x100000000) | 0;
  return k;
})();

function md5Hex(input: Uint8Array): string {
  const len = input.length;
  const bitLen = len * 8;
  const padded = new Uint8Array((Math.floor((len + 8) / 64) + 1) * 64);
  padded.set(input);
  padded[len] = 0x80;
  const dv = new DataView(padded.buffer);
  dv.setUint32(padded.length - 8, bitLen >>> 0, true);
  dv.setUint32(padded.length - 4, Math.floor(bitLen / 0x100000000) >>> 0, true);

  let a0 = 0x67452301 | 0;
  let b0 = 0xefcdab89 | 0;
  let c0 = 0x98badcfe | 0;
  let d0 = 0x10325476 | 0;

  for (let off = 0; off < padded.length; off += 64) {
    let a = a0;
    let b = b0;
    let c = c0;
    let d = d0;
    for (let i = 0; i < 64; i++) {
      let f: number;
      let g: number;
      if (i < 16) {
        f = (b & c) | (~b & d);
        g = i;
      } else if (i < 32) {
        f = (d & b) | (~d & c);
        g = (5 * i + 1) % 16;
      } else if (i < 48) {
        f = b ^ c ^ d;
        g = (3 * i + 5) % 16;
      } else {
        f = c ^ (b | ~d);
        g = (7 * i) % 16;
      }
      f = (f + a + MD5_K[i] + dv.getUint32(off + g * 4, true)) | 0;
      a = d;
      d = c;
      c = b;
      const sh = MD5_S[i];
      b = (b + ((f << sh) | (f >>> (32 - sh)))) | 0;
    }
    a0 = (a0 + a) | 0;
    b0 = (b0 + b) | 0;
    c0 = (c0 + c) | 0;
    d0 = (d0 + d) | 0;
  }

  const out = new Uint8Array(16);
  const odv = new DataView(out.buffer);
  odv.setUint32(0, a0 >>> 0, true);
  odv.setUint32(4, b0 >>> 0, true);
  odv.setUint32(8, c0 >>> 0, true);
  odv.setUint32(12, d0 >>> 0, true);
  return toHex(out);
}

export const md5 = (input: Uint8Array): Promise<string> => Promise.resolve(md5Hex(input));

export interface Intrinsic {
  md5: string;
  sha1: string;
  sha256: string;
  sha512: string;
  "gitoid-blob-sha1": string;
  "gitoid-blob-sha256": string;
}

export async function intrinsic(input: Uint8Array): Promise<Intrinsic> {
  const [m, s1, s256, s512, g1, g256] = await Promise.all([
    md5(input),
    sha1(input),
    sha256(input),
    sha512(input),
    gitoidBlobSha1(input),
    gitoidBlobSha256(input)
  ]);
  return {
    md5: m,
    sha1: s1,
    sha256: s256,
    sha512: s512,
    "gitoid-blob-sha1": g1,
    "gitoid-blob-sha256": g256
  };
}
