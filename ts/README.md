# @spicelabs/coordinates (TypeScript)

The TypeScript implementation of [`coordinates`](../). Conforms to [`../spec.yaml`](../spec.yaml) and is verified against the shared [`../vectors/`](../vectors) in CI.

Isomorphic — one module that runs in Node and the browser on the Web Crypto API, with **zero runtime dependencies** (MD5, which Web Crypto lacks, is the one vendored algorithm). Covers the intrinsic identifiers (content hashes and git blob ids) and the extrinsic ones (purl).

The hashing API is async, because Web Crypto hashing is async (there is no synchronous hashing in the browser). purl is synchronous.

In the browser, the SHA functions need a **secure context** (https, or localhost) — that's where `crypto.subtle` is available; MD5 and purl work anywhere. On Node 20+ everything works out of the box.

## Use

```bash
npm install @spicelabs/coordinates
```

Intrinsic — content hashes and git blob ids:

```ts
import { sha256, gitoidBlobSha256, intrinsic } from "@spicelabs/coordinates";

const bytes = new TextEncoder().encode("abc");
await sha256(bytes); // "ba7816bf…"
await gitoidBlobSha256(bytes); // "gitoid:blob:sha256:c1cf…"  (== `git hash-object`)
await intrinsic(bytes); // { md5, sha1, sha256, sha512, "gitoid-blob-sha1", "gitoid-blob-sha256" }
```

Streaming — feed the input in chunks (or from an async source such as a Node stream or a `ReadableStream`) and get the same six identifiers:

```ts
import { IntrinsicHasher, intrinsicStream } from "@spicelabs/coordinates";

await new IntrinsicHasher().update(chunkA).update(chunkB).finish();
await intrinsicStream(nodeReadableOrReadableStream); // any AsyncIterable<Uint8Array>
```

Unlike the Rust and Java versions, this **buffers** the chunks and hashes them at `finish()` — Web Crypto has no incremental digest, so the browser-compatible path can't hash truly incrementally. It's for chunked-feeding ergonomics and cross-language parity, not reduced memory use (and so, unlike Rust/Java, it needs no up-front content length).

Extrinsic — purl, parsed to a typed object and built back to canonical form:

```ts
import { purl } from "@spicelabs/coordinates";

purl.parse("pkg:npm/%40angular/core@17.0.0");
// { type: "npm", namespace: "@angular", name: "core", version: "17.0.0", qualifiers: {}, subpath: null }
purl.build({ type: "npm", name: "lodash", version: "4.17.21" }); // "pkg:npm/lodash@4.17.21"
```

Some types (cpan, golang, composer, maven, …) require a namespace, which a producer can't always find. Rather than failing, the lenient path uses the sentinel `~unknown`, which can never be a real namespace in those ecosystems. `refines` links such a purl to its fully-known form once the namespace turns up:

```ts
const partial = purl.parse("pkg:cpan/Moose@2.2207", { missingNamespace: "unknown" });
purl.build(partial); // "pkg:cpan/~unknown/Moose@2.2207"
purl.isNamespaceUnknown(partial); // true
purl.refines(purl.parse("pkg:cpan/ETHER/Moose@2.2207"), partial); // true
```

## Develop

```bash
pnpm install
pnpm test       # runs ../vectors against this implementation (Node)
pnpm run build
pnpm run smoke  # builds + serves; open the printed URL to run the same vectors in a real browser
```
