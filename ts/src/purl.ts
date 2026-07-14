// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

/**
 * Package URL (purl), per https://github.com/package-url/purl-spec — parse, build, normalize, and
 * validate into a fully-typed structure, applying each type's normalization + namespace rules.
 */

export interface Purl {
  type: string;
  namespace: string | null;
  name: string;
  version: string | null;
  qualifiers: Record<string, string>;
  subpath: string | null;
}

// What build() accepts: a parsed Purl, or a literal with just the parts you have (type and name).
export interface PurlInput {
  type: string;
  name: string;
  namespace?: string | null;
  version?: string | null;
  qualifiers?: Record<string, string>;
  subpath?: string | null;
}

export class PurlError extends Error {}

const TYPE = /^[a-zA-Z][a-zA-Z0-9.+-]*$/;
const QUALIFIER_KEY = /^[a-zA-Z0-9.\-_]+$/;
const UTF8 = new TextEncoder();

function fail(message: string): never {
  throw new PurlError(message);
}

function decode(s: string): string {
  try {
    return decodeURIComponent(s);
  } catch {
    return fail(`invalid percent-encoding: ${s}`);
  }
}

// Percent-encode a component, leaving the unreserved set and ":" (the canonical purl form).
function encode(s: string): string {
  let out = "";
  for (const byte of UTF8.encode(s)) {
    if (
      (byte >= 0x41 && byte <= 0x5a) || // A-Z
      (byte >= 0x61 && byte <= 0x7a) || // a-z
      (byte >= 0x30 && byte <= 0x39) || // 0-9
      byte === 0x2d || // -
      byte === 0x2e || // .
      byte === 0x5f || // _
      byte === 0x7e || // ~
      byte === 0x3a // :
    ) {
      out += String.fromCharCode(byte);
    } else {
      out += "%" + byte.toString(16).toUpperCase().padStart(2, "0");
    }
  }
  return out;
}

function normalizeSubpath(s: string): string | null {
  const segments = s
    .split("/")
    .map(decode)
    .filter((seg) => seg !== "" && seg !== "." && seg !== "..");
  return segments.length ? segments.join("/") : null;
}

function parseQualifiers(s: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const part of s.split("&")) {
    if (part === "") continue;
    const eq = part.indexOf("=");
    const key = (eq < 0 ? part : part.slice(0, eq)).toLowerCase();
    if (!QUALIFIER_KEY.test(key)) fail(`invalid qualifier key: ${key}`);
    const value = eq < 0 ? "" : decode(part.slice(eq + 1));
    if (value === "") continue; // empty values are dropped
    out[key] = value;
  }
  return out;
}

// Per-type normalization + validation, derived from the purl-spec type definitions. Only deviations
// from the default (keep case, optional namespace) are listed. Applied by parse() and build().
interface TypeRule {
  nsLower?: boolean;
  nameLower?: boolean;
  verLower?: boolean;
  nameRule?: "pypi" | "pub";
  ns?: "required" | "prohibited";
}

const TYPE_RULES: Record<string, TypeRule> = {};
const addRule = (types: string[], patch: TypeRule): void => {
  for (const t of types) TYPE_RULES[t] = { ...TYPE_RULES[t], ...patch };
};
// case_sensitive: false in the type definitions
addRule(
  [
    "alpm",
    "apk",
    "bitbucket",
    "composer",
    "deb",
    "github",
    "hex",
    "luarocks",
    "qpkg",
    "rpm",
    "vscode-extension",
    "yocto"
  ],
  { nsLower: true }
);
addRule(
  [
    "alpm",
    "apk",
    "bitbucket",
    "bitnami",
    "chrome-extension",
    "composer",
    "deb",
    "github",
    "hex",
    "luarocks",
    "oci",
    "otp",
    "pub",
    "pypi",
    "vscode-extension"
  ],
  { nameLower: true }
);
addRule(["huggingface", "oci", "pypi", "vscode-extension"], { verLower: true });
// special name normalization_rules
addRule(["pypi"], { nameRule: "pypi" }); // underscore -> dash
addRule(["pub"], { nameRule: "pub" }); // non [a-z0-9] -> underscore
// namespace requirement
addRule(
  [
    "alpm",
    "apk",
    "bitbucket",
    "composer",
    "cpan",
    "deb",
    "github",
    "golang",
    "huggingface",
    "maven",
    "qpkg",
    "rpm",
    "swift",
    "vscode-extension"
  ],
  { ns: "required" }
);
addRule(
  [
    "bazel",
    "bitnami",
    "cargo",
    "chrome-extension",
    "cocoapods",
    "conda",
    "cran",
    "gem",
    "hackage",
    "julia",
    "mlflow",
    "nuget",
    "oci",
    "opam",
    "otp",
    "pub",
    "pypi"
  ],
  { ns: "prohibited" }
);

/** Apply the type's normalization rules, then validate its namespace requirement. */
export function normalize(purl: Purl): Purl {
  const r = TYPE_RULES[purl.type] ?? {};
  let { namespace, name, version } = purl;
  if (namespace !== null && r.nsLower) namespace = namespace.toLowerCase();
  if (r.nameLower) name = name.toLowerCase();
  if (r.nameRule === "pypi") name = name.replace(/_/g, "-");
  else if (r.nameRule === "pub") name = name.replace(/[^a-z0-9]/g, "_");
  if (version !== null && r.verLower) version = version.toLowerCase();

  // Bespoke per-type rules that do not reduce to a flag (from the type definitions).
  if (purl.type === "mlflow" && /databricks/i.test(purl.qualifiers["repository_url"] ?? "")) {
    name = name.toLowerCase(); // Databricks-hosted MLflow names are case-insensitive
  }
  if (purl.type === "cpan" && name.includes("::")) {
    fail("cpan name must be a distribution name, not a module name (no ::)");
  }
  if (purl.type === "chrome-extension") {
    if (!/^[a-z]{32}$/.test(name)) fail("chrome-extension name must be 32 letters (a-z)");
    if (version !== null && !/^\d+(\.\d+){0,3}$/.test(version)) {
      fail("chrome-extension version must be 1-4 dot-separated numbers");
    }
  }
  if (purl.type === "julia" && !purl.qualifiers["uuid"]) {
    fail("julia purls require a uuid qualifier");
  }

  if (r.ns === "prohibited" && namespace !== null) {
    fail(`purl type "${purl.type}" does not allow a namespace`);
  }
  if (r.ns === "required" && (namespace === null || namespace === "")) {
    fail(`purl type "${purl.type}" requires a namespace`);
  }
  return {
    type: purl.type,
    namespace,
    name,
    version,
    qualifiers: purl.qualifiers,
    subpath: purl.subpath
  };
}

export function parse(input: string): Purl {
  if (typeof input !== "string" || input.length === 0) fail("empty purl");

  const colon = input.indexOf(":");
  if (colon < 0 || input.slice(0, colon).toLowerCase() !== "pkg") {
    fail('a purl must start with the "pkg:" scheme');
  }
  let rest = input.slice(colon + 1).replace(/^\/+/, "");

  let subpath: string | null = null;
  const hash = rest.indexOf("#");
  if (hash >= 0) {
    subpath = normalizeSubpath(rest.slice(hash + 1));
    rest = rest.slice(0, hash);
  }

  let qualifiers: Record<string, string> = {};
  const question = rest.indexOf("?");
  if (question >= 0) {
    qualifiers = parseQualifiers(rest.slice(question + 1));
    rest = rest.slice(0, question);
  }

  const slash = rest.indexOf("/");
  if (slash < 0) fail("a purl must have a type and a name");
  const type = rest.slice(0, slash).toLowerCase();
  if (!TYPE.test(type)) fail(`invalid type: ${type}`);
  rest = rest.slice(slash + 1);

  let namespace: string | null = null;
  let nameVersion = rest;
  const lastSlash = rest.lastIndexOf("/");
  if (lastSlash >= 0) {
    const ns = rest
      .slice(0, lastSlash)
      .split("/")
      .filter((seg) => seg.length > 0)
      .map(decode)
      .join("/");
    namespace = ns.length ? ns : null;
    nameVersion = rest.slice(lastSlash + 1);
  }

  // The version's "@" lives in the name segment, so split it only after the namespace is removed —
  // otherwise a namespace like npm's "@babel" would be misread as the version.
  let version: string | null = null;
  const at = nameVersion.lastIndexOf("@");
  if (at >= 0) {
    version = decode(nameVersion.slice(at + 1));
    nameVersion = nameVersion.slice(0, at);
  }
  const name = decode(nameVersion);
  if (name.length === 0) fail("a purl must have a name");

  return normalize({ type, namespace, name, version, qualifiers, subpath });
}

export function build(input: PurlInput): string {
  return buildInternal(input, false);
}

/**
 * A Maven-resolvable rendering of a purl: identical to {@link build} except that for
 * `pkg:maven` the `+` character in the version is left unencoded. All other components and
 * unsafe characters are encoded exactly as in the canonical form.
 */
export function toMavenUrl(input: PurlInput): string {
  return buildInternal(input, true);
}

function buildInternal(input: PurlInput, mavenVersion: boolean): string {
  const type = (input.type ?? "").toLowerCase();
  if (!type || !TYPE.test(type)) fail(`invalid type: ${input.type}`);
  if (!input.name) fail("a purl must have a name");

  const purl = normalize({
    type,
    namespace: input.namespace ?? null,
    name: input.name,
    version: input.version ?? null,
    qualifiers: input.qualifiers ?? {},
    subpath: input.subpath ?? null
  });

  let out = "pkg:" + purl.type;
  if (purl.namespace) {
    out +=
      "/" +
      purl.namespace
        .split("/")
        .filter((s) => s.length > 0)
        .map(encode)
        .join("/");
  }
  out += "/" + encode(purl.name);
  if (purl.version) {
    out +=
      "@" +
      (mavenVersion && purl.type === "maven"
        ? encodeMavenVersion(purl.version)
        : encode(purl.version));
  }

  const q = purl.qualifiers ?? {};
  const entries = Object.keys(q)
    .filter((k) => q[k] !== "" && q[k] != null)
    .map((k) => {
      const key = k.toLowerCase();
      if (!QUALIFIER_KEY.test(key)) fail(`invalid qualifier key: ${k}`);
      return [key, q[k]] as [string, string];
    })
    .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
  if (entries.length) {
    out += "?" + entries.map(([k, v]) => k + "=" + encode(v)).join("&");
  }
  if (purl.subpath) {
    const sp = normalizeSubpath(purl.subpath);
    if (sp) out += "#" + sp.split("/").map(encode).join("/");
  }
  return out;
}

function encodeMavenVersion(version: string): string {
  return version.split("+").map(encode).join("+");
}
