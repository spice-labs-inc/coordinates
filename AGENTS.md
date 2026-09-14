# Agent notes for coordinates

## Test IDs and shared test data

Downstream tools run black-box integration tests that reuse **this repository's own test
data**. They do not copy the data into their trees: a consumer reads `test-fixtures.json`
at the root of this repository, at the exact commit the shipped jar was built from (the
commit recorded in `META-INF/git/<artifactId>.properties`), and exports what that file
declares. Every unit test here and every downstream case derived from it share one ID:

```
coordinates/<qualified suite or class>#<test name>     code tests
coordinates/<data path>[#<case id>]                    data-driven cases: the file's `id` field
```

Consumers diff those IDs against their own coverage manifests and fail their CI on orphans,
so keep these rules when you write or change tests:

- **Put expectations in data, not in code.** A new fixture-driven expectation belongs in a
  data file the unit test reads (see below for this repository's format); a downstream tool
  can then evaluate the same file without a second copy.
- **Every data file gets an `id`**, derived from its path exactly as the existing files do,
  and the unit test that reads it must require it. Never hand-pick ids.
- **Declare new data in `test-fixtures.json`**: an `expectations[]` entry (glob, format,
  `idField`, how the fixture path derives) and a `fixtures[]` entry with a tier (0 committed
  and small, 1 downloadable for a nightly run, 2 full corpora). Undeclared data is invisible
  to consumers.
- **Keep tier-0 fixtures small** (kilobytes to a few megabytes); anything large is a download
  (`downloads[]` with a sha256) or LFS, at tier 1 or 2.
- **Renaming a test or data file changes its ID.** That is allowed, but downstream coverage
  manifests will report an orphan; mention it in the PR so they can be regenerated.
- **Keep the provenance plugin.** The jar must record its commit in
  `META-INF/git/<artifactId>.properties`; without it consumers fall back to the release tag.

### Here

- Case IDs are `vectors/intrinsic.json#<name>` and `vectors/purl-ns-rules.json#<description>`;
  `vectors/validate.mjs` rejects duplicates, so keep names and descriptions unique.
  `extrinsic.json` and `purl-types.json` are vendored from purl-spec and addressed by index,
  which changes on an upstream resync.
- Only canonical purls of real artifacts are exercised downstream, so most vectors are
  library-level by design.
