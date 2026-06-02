# @spice-labs/coordinates (TypeScript)

The TypeScript implementation of [`coordinates`](../). Conforms to [`../spec.yaml`](../spec.yaml) and is verified against the shared [`../vectors/`](../vectors) in CI.

Isomorphic — one module that runs in Node and the browser on the Web Crypto API, with **zero runtime dependencies** (MD5, which Web Crypto lacks, is the one vendored algorithm). Covers the intrinsic identifiers (content hashes and git blob ids) and the extrinsic ones (purl).

The hashing API is async, because Web Crypto hashing is async (there is no synchronous hashing in the browser). purl is synchronous.

In the browser, the SHA functions need a **secure context** (https, or localhost) — that's where `crypto.subtle` is available; MD5 and purl work anywhere. On Node 20+ everything works out of the box.

## Use

```bash
npm install @spice-labs/coordinates
```

Intrinsic — content hashes and git blob ids:

```ts
import { sha256, gitoidBlobSha256, intrinsic } from "@spice-labs/coordinates";

const bytes = new TextEncoder().encode("abc");
await sha256(bytes); // "ba7816bf…"
await gitoidBlobSha256(bytes); // "gitoid:blob:sha256:c1cf…"  (== `git hash-object`)
await intrinsic(bytes); // { md5, sha1, sha256, sha512, "gitoid-blob-sha1", "gitoid-blob-sha256" }
```

Extrinsic — purl, parsed to a typed object and built back to canonical form:

```ts
import { purl } from "@spice-labs/coordinates";

purl.parse("pkg:npm/%40angular/core@17.0.0");
// { type: "npm", namespace: "@angular", name: "core", version: "17.0.0", qualifiers: {}, subpath: null }
purl.build({ type: "npm", name: "lodash", version: "4.17.21" }); // "pkg:npm/lodash@4.17.21"
```

## Develop

```bash
pnpm install
pnpm test       # runs ../vectors against this implementation (Node)
pnpm run build
pnpm run smoke  # builds + serves; open the printed URL to run the same vectors in a real browser
```
