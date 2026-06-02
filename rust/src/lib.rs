// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Spice Labs, Inc. & Contributors

//! Intrinsic content identifiers, per ../../spec.yaml.
//!
//! Rust std has no hashing, so the digests come from the audited RustCrypto crates (`sha1`,
//! `sha2`, `md-5`) — this crate's only runtime dependencies. A `gitoid:blob:<name>` is a git blob
//! object id: the digest of `"blob " + len + NUL + content`, the exact value `git hash-object`
//! produces. A plain digest is the lowercase hex of the digest of the content. See ../../spec.yaml
//! for the definitions and ../../vectors for the proof.

use md5::Md5;
use sha1::Sha1;
use sha2::Digest;
use sha2::{Sha256, Sha512};

/// Extrinsic identifiers — purl (package URL).
pub mod purl;

/// Lowercase-hex digest of the parts, fed to one digest run in order.
fn digest<D: Digest>(parts: &[&[u8]]) -> Vec<u8> {
    let mut d = D::new();
    for &part in parts {
        d.update(part);
    }
    d.finalize().to_vec()
}

fn hex(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 2);
    for &b in bytes {
        s.push(char::from_digit((b >> 4) as u32, 16).unwrap());
        s.push(char::from_digit((b & 0x0f) as u32, 16).unwrap());
    }
    s
}

/// The git loose-object header for a blob of `len` bytes: ASCII "blob <len>" then a single NUL.
fn blob_header(len: usize) -> Vec<u8> {
    let mut header = format!("blob {}", len).into_bytes();
    header.push(0);
    header
}

pub fn md5(input: &[u8]) -> String {
    hex(&digest::<Md5>(&[input]))
}

pub fn sha1(input: &[u8]) -> String {
    hex(&digest::<Sha1>(&[input]))
}

pub fn sha256(input: &[u8]) -> String {
    hex(&digest::<Sha256>(&[input]))
}

pub fn sha512(input: &[u8]) -> String {
    hex(&digest::<Sha512>(&[input]))
}

pub fn gitoid_blob_sha1(input: &[u8]) -> String {
    let header = blob_header(input.len());
    format!(
        "gitoid:blob:sha1:{}",
        hex(&digest::<Sha1>(&[header.as_slice(), input]))
    )
}

pub fn gitoid_blob_sha256(input: &[u8]) -> String {
    let header = blob_header(input.len());
    format!(
        "gitoid:blob:sha256:{}",
        hex(&digest::<Sha256>(&[header.as_slice(), input]))
    )
}

/// Every intrinsic identifier for `input`, keyed by its spec.yaml name.
pub fn intrinsic(input: &[u8]) -> Vec<(&'static str, String)> {
    vec![
        ("md5", md5(input)),
        ("sha1", sha1(input)),
        ("sha256", sha256(input)),
        ("sha512", sha512(input)),
        ("gitoid-blob-sha1", gitoid_blob_sha1(input)),
        ("gitoid-blob-sha256", gitoid_blob_sha256(input)),
    ]
}
