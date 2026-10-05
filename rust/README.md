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

Some types (cpan, golang, composer, maven, …) require a namespace, which a producer can't always find. Rather than failing, the lenient path uses the sentinel `~unknown`, which can never be a real namespace in those ecosystems. `refines` links such a purl to its fully-known form once the namespace turns up:

```rust
use coordinates::purl::MissingNamespace;

let partial = purl::parse_with("pkg:cpan/Moose@2.2207", MissingNamespace::Unknown).unwrap();
purl::build(&partial).unwrap(); // "pkg:cpan/~unknown/Moose@2.2207"
assert!(partial.is_namespace_unknown());
assert!(purl::refines(&purl::parse("pkg:cpan/ETHER/Moose@2.2207").unwrap(), &partial));
```

## Develop

```bash
cargo test     # runs ../vectors against this implementation
cargo build
```
