# Vectors

The proof behind [`../spec.yaml`](../spec.yaml): every implementation runs these and must reproduce them exactly — CI fails on any mismatch. The spec says _what_; this says _prove it_.

## Intrinsic — [`intrinsic.json`](intrinsic.json)

Each case is an input (lowercase-hex bytes, `""` = empty) and the expected output of every intrinsic identifier:

```json
{
  "name": "abc",
  "input_hex": "616263",
  "expect": { "sha256": "ba78…", "gitoid-blob-sha256": "gitoid:blob:sha256:c1cf…" }
}
```

To consume: decode `input_hex` to bytes, compute each identifier per the spec, assert it equals `expect[<id>]`. Validated by [`vectors.schema.json`](vectors.schema.json). The `gitoid-blob-*` values are independently checkable with `git hash-object`.

## Extrinsic — [`extrinsic.json`](extrinsic.json)

We don't restate purl. Two vendored snapshots of the official [`package-url/purl-spec`](https://github.com/package-url/purl-spec) suite (MIT-licensed, @ `4a5927c3`, 2026-06-02), run unchanged by every implementation:

- [`extrinsic.json`](extrinsic.json) — the base suite (`tests/spec/specification-test.json`): scheme/type/qualifier rules.
- [`purl-types.json`](purl-types.json) — per-type normalization + validation (`tests/types/*.json`, all 39 types concatenated, 503 cases): case folding, name rules (pypi `_`→`-`, pub slugging), namespace required/prohibited, and the bespoke grammars (chrome-extension ids, cpan, julia uuid, mlflow/Databricks). To re-sync: copy the upstream files and re-concatenate.

Plus one suite we author (**not** vendored), derived from the same type definitions:

- [`purl-ns-rules.json`](purl-ns-rules.json) — 31 synthetic parse-failure cases, one per type, each isolating the namespace **required**/**prohibited** rule. The vendored suite only exercises that rule for the few types with upstream failure cases, so this is what catches a per-implementation rule-table typo (drift between the TS/Java/Rust tables) in the shared conformance run.

Each case is a `parse`, `build`, or `roundtrip` over a purl string and its components.

## What a conformance test covers

Fixed vectors are necessary but not sufficient — each implementation's suite has three layers:

1. **Known answers** — `intrinsic.json` (today `empty` + `abc`; growing to one-byte, NUL-containing, and larger binary inputs) plus the purl suite.
2. **Properties** — universal invariants over random inputs, not fixed cases:
   - purl: `parse(build(x)) == x`; `build(parse(s))` is canonical (idempotent).
   - hashes: every output matches its ABNF grammar (length + alphabet), for every input.
3. **Differential** — the _same_ random inputs through every language's implementation; they must all agree. Disagreement is, by definition, a drift bug.

The spec defines it, the known answers pin it, the properties generalize it, and differential testing proves all four languages compute the same thing.
