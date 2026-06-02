# 🧭 coordinates

Canonical, verifiable identifiers for software artifacts — the **intrinsic** content hashes and **extrinsic** package coordinates that Spice Labs tooling agrees on. One definition, shared test vectors, and conforming implementations in every language we use.

We map software by what it _is_, not what it's labelled — so the identifiers everything joins on have to be computed _identically_ everywhere. `coordinates` is the single source of truth: define each identifier once, prove it with shared vectors, and make every implementation conform.

## How it fits together

- **`spec.yaml`** — the definition: every identifier and its exact construction. The single source of truth.
- **`vectors/`** — the proof: `input → expected output` cases (JSON-Schema-validated) every implementation must reproduce.
- **`ts/ java/ rust/`** — the implementations: built from the spec, checked against the vectors in CI. One per runtime (JS, JVM, native); Scala and other JVM languages use the Java one.

If an implementation disagrees with the vectors — or the vectors with the spec — CI goes red. Nothing drifts.

## Using it

One package per runtime; Scala (and any JVM language) uses the Java one.

**TypeScript / JavaScript** — `@spice-labs/coordinates` (npm), isomorphic (Node + browser), async:

```bash
npm install @spice-labs/coordinates
```

```ts
import { sha256, gitoidBlobSha256, purl } from "@spice-labs/coordinates";
await sha256(bytes); // "ba7816bf…"
await gitoidBlobSha256(bytes); // "gitoid:blob:sha256:…"  (== git hash-object)

purl.parse("pkg:npm/%40angular/core@17.0.0");
// { type: "npm", namespace: "@angular", name: "core", version: "17.0.0", qualifiers: {}, subpath: null }
purl.build({ type: "npm", name: "lodash", version: "4.17.21" }); // "pkg:npm/lodash@4.17.21"
```

**Java** — `io.spicelabs:coordinates` (Maven), JDK-only:

```xml
<dependency>
  <groupId>io.spicelabs</groupId>
  <artifactId>coordinates</artifactId>
  <version>0.1.0</version>
</dependency>
```

```java
import io.spicelabs.coordinates.Coordinates;
import io.spicelabs.coordinates.Purl;
Coordinates.sha256(bytes); // "ba7816bf…"
Coordinates.gitoidBlobSha256(bytes); // "gitoid:blob:sha256:…"

Purl p = Purl.parse("pkg:maven/org.apache.commons/io@1.3.4");
p.namespace; p.name; p.version; // "org.apache.commons", "io", "1.3.4"
p.toCanonical(); // "pkg:maven/org.apache.commons/io@1.3.4"
```

**Scala** — the Java artifact, no separate build (note the single `%`):

```scala
libraryDependencies += "io.spicelabs" % "coordinates" % "0.1.0"

import io.spicelabs.coordinates.{Coordinates, Purl}
Coordinates.sha256(bytes) // "ba7816bf…"
Purl.parse("pkg:maven/org.apache.commons/io@1.3.4").name // "io"
```

**Rust** — `spice-coordinates` (crates.io), imported as `coordinates`:

```bash
cargo add spice-coordinates
```

```rust
use coordinates::purl;
coordinates::sha256(bytes); // "ba7816bf…"
coordinates::gitoid_blob_sha256(bytes); // "gitoid:blob:sha256:…"

let p = purl::parse("pkg:cargo/serde@1.0.0").unwrap();
(p.name, p.version); // ("serde", Some("1.0.0"))
purl::build(&p).unwrap(); // "pkg:cargo/serde@1.0.0"
```

## Scope

- **Intrinsic** — content hashes and git-style blob ids, derived purely from bytes: identical content yields an identical id, anywhere. The full set is in [`spec.yaml`](spec.yaml). `md5`, `sha1`, and `gitoid-blob-sha1` are **content aliases for lookup, not security primitives** — they use collision-broken digests, so never rely on them for integrity or trust.
- **Extrinsic** — package coordinates (purl), conforming to [package-url/purl-spec](https://github.com/package-url/purl-spec): parse, build, and validate into a fully-typed structure.

## Conformance

The contract is `spec.yaml` plus the vectors — never any one implementation. To write your own (in any language) or verify an existing one:

1. For each case in [`vectors/intrinsic.json`](vectors/intrinsic.json), decode `input_hex` to bytes.
2. Compute each identifier as defined in [`spec.yaml`](spec.yaml).
3. Assert it equals the value in `expect`.

That's what every implementation here does in CI (see [`vectors/README.md`](vectors/README.md) for the full approach — known answers, universal properties, and cross-implementation differential checks). The `gitoid-blob-*` values are independently checkable with stock `git hash-object`; purl conformance is the upstream [purl-spec](https://github.com/package-url/purl-spec) suite.

## Contributing & license

See [CONTRIBUTING.md](CONTRIBUTING.md). Apache-2.0 — see [LICENSE](LICENSE).

---

A [Spice Labs](https://spicelabs.io) project. © 2026 Spice Labs, Inc. &amp; Contributors.
