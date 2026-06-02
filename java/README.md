# coordinates (Java)

The Java implementation of [`coordinates`](../). Conforms to [`../spec.yaml`](../spec.yaml) and is verified against the shared [`../vectors/`](../vectors) in CI.

JDK-only — `java.security.MessageDigest`, **zero runtime dependencies**, Java 8 compatible (so it can be shaded into the ancho agent). Covers the intrinsic identifiers (content hashes and git blob ids) and the extrinsic ones (purl).

## Use

```xml
<dependency>
  <groupId>io.spicelabs</groupId>
  <artifactId>coordinates</artifactId>
  <version>0.1.0</version>
</dependency>
```

Intrinsic — content hashes and git blob ids:

```java
import io.spicelabs.coordinates.Coordinates;

byte[] bytes = "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8);
Coordinates.sha256(bytes);           // "ba7816bf…"
Coordinates.gitoidBlobSha256(bytes); // "gitoid:blob:sha256:c1cf…"  (== `git hash-object`)
Coordinates.intrinsic(bytes);        // { md5, sha1, sha256, sha512, gitoid-blob-sha1, gitoid-blob-sha256 }
```

Extrinsic — purl, parsed to a typed value and built back to canonical form:

```java
import io.spicelabs.coordinates.Purl;

Purl p = Purl.parse("pkg:maven/org.apache.commons/io@1.3.4");
p.namespace; // "org.apache.commons"
p.name;      // "io"
p.version;   // "1.3.4"
p.toCanonical(); // "pkg:maven/org.apache.commons/io@1.3.4"

new Purl("npm", null, "lodash", "4.17.21", null, null).toCanonical(); // "pkg:npm/lodash@4.17.21"
```

## Develop

```bash
mvn test       # runs ../vectors against this implementation
mvn package
```
