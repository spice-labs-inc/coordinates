// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Spice Labs, Inc. & Contributors

//! Intrinsic content identifiers, per ../../spec.yaml.
//!
//! Rust std has no hashing, so the digests come from the audited RustCrypto crates (`sha1`,
//! `sha2`, `md-5`) — this crate's only runtime dependencies. A `gitoid:blob:<name>` is a git blob
//! object id: the digest of `"blob " + len + NUL + content`, the exact value `git hash-object`
//! produces. A plain digest is the lowercase hex of the digest of the content. See ../../spec.yaml
//! for the definitions and ../../vectors for the proof.

use std::io::{self, Read};

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
fn blob_header(len: u64) -> Vec<u8> {
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
    let header = blob_header(input.len() as u64);
    format!(
        "gitoid:blob:sha1:{}",
        hex(&digest::<Sha1>(&[header.as_slice(), input]))
    )
}

pub fn gitoid_blob_sha256(input: &[u8]) -> String {
    let header = blob_header(input.len() as u64);
    format!(
        "gitoid:blob:sha256:{}",
        hex(&digest::<Sha256>(&[header.as_slice(), input]))
    )
}

/// Every intrinsic identifier for `input`, keyed by its spec.yaml name.
pub fn intrinsic(input: &[u8]) -> Vec<(&'static str, String)> {
    let mut hasher = IntrinsicHasher::new(input.len() as u64);
    hasher.update(input);
    hasher.finish()
}

/// Streaming computation of every intrinsic identifier in a single pass over the input.
///
/// The plain digests would stream with no length known in advance, but `gitoid` framing embeds
/// the total content length (`"blob " + len + NUL + content`, see ../../spec.yaml), which must be
/// digested before any content — so the length is required up front, at [`new`](Self::new). Feed
/// the bytes with [`update`](Self::update) (any chunking) and read the identifiers with
/// [`finish`](Self::finish); the result matches [`intrinsic`] exactly.
#[must_use]
pub struct IntrinsicHasher {
    md5: Md5,
    sha1: Sha1,
    sha256: Sha256,
    sha512: Sha512,
    gitoid_sha1: Sha1,
    gitoid_sha256: Sha256,
}

impl IntrinsicHasher {
    /// A hasher for content of exactly `content_len` bytes. The two gitoid digests are seeded with
    /// the git blob header for that length; the plain digests start empty.
    pub fn new(content_len: u64) -> Self {
        let header = blob_header(content_len);
        let mut gitoid_sha1 = Sha1::new();
        let mut gitoid_sha256 = Sha256::new();
        gitoid_sha1.update(&header);
        gitoid_sha256.update(&header);
        Self {
            md5: Md5::new(),
            sha1: Sha1::new(),
            sha256: Sha256::new(),
            sha512: Sha512::new(),
            gitoid_sha1,
            gitoid_sha256,
        }
    }

    /// Feed the next chunk of content to every digest. Call zero or more times; the chunks
    /// concatenated must total the `content_len` given to [`new`](Self::new).
    pub fn update(&mut self, chunk: &[u8]) {
        self.md5.update(chunk);
        self.sha1.update(chunk);
        self.sha256.update(chunk);
        self.sha512.update(chunk);
        self.gitoid_sha1.update(chunk);
        self.gitoid_sha256.update(chunk);
    }

    /// Finalize and return every intrinsic identifier, keyed by its spec.yaml name — the same shape
    /// and order as [`intrinsic`].
    pub fn finish(self) -> Vec<(&'static str, String)> {
        vec![
            ("md5", hex(&self.md5.finalize())),
            ("sha1", hex(&self.sha1.finalize())),
            ("sha256", hex(&self.sha256.finalize())),
            ("sha512", hex(&self.sha512.finalize())),
            (
                "gitoid-blob-sha1",
                format!("gitoid:blob:sha1:{}", hex(&self.gitoid_sha1.finalize())),
            ),
            (
                "gitoid-blob-sha256",
                format!("gitoid:blob:sha256:{}", hex(&self.gitoid_sha256.finalize())),
            ),
        ]
    }
}

/// Every intrinsic identifier for the `content_len` bytes read from `reader`, in a single pass —
/// the streaming counterpart to [`intrinsic`], for hashing a file or socket without buffering it
/// all. `content_len` must be the exact number of bytes `reader` yields (e.g. a file's size);
/// gitoid framing depends on it.
pub fn intrinsic_reader<R: Read>(
    mut reader: R,
    content_len: u64,
) -> io::Result<Vec<(&'static str, String)>> {
    let mut hasher = IntrinsicHasher::new(content_len);
    let mut buf = [0u8; 8192];
    loop {
        let n = reader.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hasher.finish())
}
