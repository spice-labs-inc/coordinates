# coordinates (Rust)

The Rust implementation of [`coordinates`](../). Conforms to [`../spec.yaml`](../spec.yaml) and is verified against the shared [`../vectors/`](../vectors) in CI.

Rust std has no hashing, so the digests come from the audited RustCrypto crates (`sha1`, `sha2`, `md-5`) — the crate's only runtime dependencies (hex, purl, and the test's vector reader are hand-rolled, so there are no others). Covers the intrinsic identifiers (content hashes and git blob ids) and the extrinsic ones (purl). Published as `spice-coordinates`, imported as `coordinates`.

## Use

```bash
cargo add spice-coordinates
```

Intrinsic — content hashes and git blob ids:

```rust
let bytes = b"abc";
coordinates::sha256(bytes);             // "ba7816bf…"
coordinates::gitoid_blob_sha256(bytes); // "gitoid:blob:sha256:c1cf…"  (== `git hash-object`)
coordinates::intrinsic(bytes);          // [(name, value); 6]
```

Streaming — for a large artifact you don't want to hold in memory, feed it in chunks (or straight from a reader) and get the same six identifiers in one pass. The length is required up front because `gitoid` framing embeds it:

```rust
let len = std::fs::metadata("artifact.bin")?.len();
let file = std::fs::File::open("artifact.bin")?;
coordinates::intrinsic_reader(file, len)?;       // [(name, value); 6]

// or drive it yourself, any chunking:
let mut h = coordinates::IntrinsicHasher::new(len);
h.update(chunk_a);
h.update(chunk_b);
h.finish();                                       // [(name, value); 6]
```

Extrinsic — purl, parsed to a typed struct and built back to canonical form:

```rust
use coordinates::purl;

let p = purl::parse("pkg:cargo/serde@1.0.0").unwrap();
assert_eq!(p.name, "serde");
assert_eq!(p.version.as_deref(), Some("1.0.0"));
purl::build(&p).unwrap(); // "pkg:cargo/serde@1.0.0"
```

## Develop

```bash
cargo test     # runs ../vectors against this implementation
cargo build
```
