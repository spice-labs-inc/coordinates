// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Spice Labs, Inc. & Contributors

//! Package URL (purl), per <https://github.com/package-url/purl-spec> — parse, build, normalize, and
//! validate into a fully-typed value, applying each type's normalization + namespace rules. No deps.

use std::collections::BTreeMap;
use std::fmt;

/// A parsed Package URL. `qualifiers` is keyed by lowercased qualifier name.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Purl {
    pub r#type: String,
    pub namespace: Option<String>,
    pub name: String,
    pub version: Option<String>,
    pub qualifiers: BTreeMap<String, String>,
    pub subpath: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PurlError(pub String);

impl fmt::Display for PurlError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl std::error::Error for PurlError {}

fn err(message: &str) -> PurlError {
    PurlError(message.to_string())
}

// Per-type normalization + validation, derived from the purl-spec type definitions. Only the types
// that deviate from the default (keep case, optional namespace) are listed.
const NS_LOWER: &[&str] = &[
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
    "yocto",
];
const NAME_LOWER: &[&str] = &[
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
    "vscode-extension",
];
const VER_LOWER: &[&str] = &["huggingface", "oci", "pypi", "vscode-extension"];
const NS_REQUIRED: &[&str] = &[
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
    "vscode-extension",
];
const NS_PROHIBITED: &[&str] = &[
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
    "pypi",
];

/// Apply the type's normalization rules, then validate its namespace requirement.
pub fn normalize(purl: Purl) -> Result<Purl, PurlError> {
    let Purl {
        r#type,
        mut namespace,
        mut name,
        mut version,
        qualifiers,
        subpath,
    } = purl;
    let ty = r#type.as_str();

    if NS_LOWER.contains(&ty) {
        if let Some(ns) = namespace.as_mut() {
            *ns = ns.to_ascii_lowercase();
        }
    }
    if NAME_LOWER.contains(&ty) {
        name = name.to_ascii_lowercase();
    }
    if ty == "pypi" {
        name = name.replace('_', "-");
    } else if ty == "pub" {
        name = name
            .chars()
            .map(|c| {
                if c.is_ascii_lowercase() || c.is_ascii_digit() {
                    c
                } else {
                    '_'
                }
            })
            .collect();
    }
    if VER_LOWER.contains(&ty) {
        if let Some(v) = version.as_mut() {
            *v = v.to_ascii_lowercase();
        }
    }

    // Bespoke per-type rules that do not reduce to a flag (from the type definitions).
    if ty == "mlflow" {
        if let Some(repo) = qualifiers.get("repository_url") {
            if repo.to_ascii_lowercase().contains("databricks") {
                name = name.to_ascii_lowercase();
            }
        }
    }
    if ty == "cpan" && name.contains("::") {
        return Err(err(
            "cpan name must be a distribution name, not a module name (no ::)",
        ));
    }
    if ty == "chrome-extension" {
        if !is_chrome_name(&name) {
            return Err(err("chrome-extension name must be 32 letters (a-z)"));
        }
        if let Some(v) = &version {
            if !is_chrome_version(v) {
                return Err(err(
                    "chrome-extension version must be 1-4 dot-separated numbers",
                ));
            }
        }
    }
    if ty == "julia" && !qualifiers.contains_key("uuid") {
        return Err(err("julia purls require a uuid qualifier"));
    }

    if NS_PROHIBITED.contains(&ty) && namespace.is_some() {
        return Err(err(&format!(
            "purl type \"{}\" does not allow a namespace",
            ty
        )));
    }
    if NS_REQUIRED.contains(&ty) && namespace.as_deref().unwrap_or("").is_empty() {
        return Err(err(&format!("purl type \"{}\" requires a namespace", ty)));
    }

    Ok(Purl {
        r#type,
        namespace,
        name,
        version,
        qualifiers,
        subpath,
    })
}

fn is_chrome_name(s: &str) -> bool {
    s.len() == 32 && s.bytes().all(|b| b.is_ascii_lowercase())
}

fn is_chrome_version(s: &str) -> bool {
    let segments: Vec<&str> = s.split('.').collect();
    !segments.is_empty()
        && segments.len() <= 4
        && segments
            .iter()
            .all(|seg| !seg.is_empty() && seg.bytes().all(|b| b.is_ascii_digit()))
}

pub fn parse(input: &str) -> Result<Purl, PurlError> {
    if input.is_empty() {
        return Err(err("empty purl"));
    }
    let scheme = input.find(':');
    let colon = match scheme {
        Some(i) if input[..i].eq_ignore_ascii_case("pkg") => i,
        _ => return Err(err("a purl must start with the \"pkg:\" scheme")),
    };
    let mut rest = input[colon + 1..].trim_start_matches('/');

    let mut subpath: Option<String> = None;
    if let Some(h) = rest.find('#') {
        subpath = normalize_subpath(&rest[h + 1..])?;
        rest = &rest[..h];
    }

    let mut qualifiers: BTreeMap<String, String> = BTreeMap::new();
    if let Some(q) = rest.find('?') {
        qualifiers = parse_qualifiers(&rest[q + 1..])?;
        rest = &rest[..q];
    }

    let slash = rest
        .find('/')
        .ok_or_else(|| err("a purl must have a type and a name"))?;
    let r#type = rest[..slash].to_ascii_lowercase();
    if !is_valid_type(&r#type) {
        return Err(err(&format!("invalid type: {}", r#type)));
    }
    rest = &rest[slash + 1..];

    let (namespace, name_version) = match rest.rfind('/') {
        Some(ls) => {
            let mut segments: Vec<String> = Vec::new();
            for seg in rest[..ls].split('/') {
                if seg.is_empty() {
                    continue;
                }
                segments.push(decode(seg)?);
            }
            let namespace = if segments.is_empty() {
                None
            } else {
                Some(segments.join("/"))
            };
            (namespace, &rest[ls + 1..])
        }
        None => (None, rest),
    };

    // The version's "@" lives in the name segment, so split it only after the namespace is removed —
    // otherwise a namespace like npm's "@babel" would be misread as the version.
    let (name_part, version) = match name_version.rfind('@') {
        Some(at) => (&name_version[..at], Some(decode(&name_version[at + 1..])?)),
        None => (name_version, None),
    };
    let name = decode(name_part)?;
    if name.is_empty() {
        return Err(err("a purl must have a name"));
    }

    normalize(Purl {
        r#type,
        namespace,
        name,
        version,
        qualifiers,
        subpath,
    })
}

pub fn build(purl: &Purl) -> Result<String, PurlError> {
    purl.to_canonical()
}

impl Purl {
    /// The canonical purl string for this value.
    pub fn to_canonical(&self) -> Result<String, PurlError> {
        let r#type = self.r#type.to_ascii_lowercase();
        if !is_valid_type(&r#type) {
            return Err(err(&format!("invalid type: {}", self.r#type)));
        }
        if self.name.is_empty() {
            return Err(err("a purl must have a name"));
        }
        let p = normalize(Purl {
            r#type,
            namespace: self.namespace.clone(),
            name: self.name.clone(),
            version: self.version.clone(),
            qualifiers: self.qualifiers.clone(),
            subpath: self.subpath.clone(),
        })?;

        let mut out = String::from("pkg:");
        out.push_str(&p.r#type);

        if let Some(namespace) = &p.namespace {
            let segments: Vec<String> = namespace
                .split('/')
                .filter(|s| !s.is_empty())
                .map(encode)
                .collect();
            if !segments.is_empty() {
                out.push('/');
                out.push_str(&segments.join("/"));
            }
        }

        out.push('/');
        out.push_str(&encode(&p.name));

        if let Some(version) = &p.version {
            if !version.is_empty() {
                out.push('@');
                out.push_str(&encode(version));
            }
        }

        let mut sorted: BTreeMap<String, &String> = BTreeMap::new();
        for (key, value) in &p.qualifiers {
            if value.is_empty() {
                continue;
            }
            let key = key.to_ascii_lowercase();
            if !is_valid_qualifier_key(&key) {
                return Err(err(&format!("invalid qualifier key: {}", key)));
            }
            sorted.insert(key, value);
        }
        if !sorted.is_empty() {
            out.push('?');
            let pairs: Vec<String> = sorted
                .iter()
                .map(|(key, value)| format!("{}={}", key, encode(value)))
                .collect();
            out.push_str(&pairs.join("&"));
        }

        if let Some(subpath) = &p.subpath {
            if let Some(normalized) = normalize_subpath(subpath)? {
                out.push('#');
                let segments: Vec<String> = normalized.split('/').map(encode).collect();
                out.push_str(&segments.join("/"));
            }
        }

        Ok(out)
    }
}

fn parse_qualifiers(s: &str) -> Result<BTreeMap<String, String>, PurlError> {
    let mut out = BTreeMap::new();
    for part in s.split('&') {
        if part.is_empty() {
            continue;
        }
        let (raw_key, raw_value) = match part.find('=') {
            Some(eq) => (&part[..eq], Some(&part[eq + 1..])),
            None => (part, None),
        };
        let key = raw_key.to_ascii_lowercase();
        if !is_valid_qualifier_key(&key) {
            return Err(err(&format!("invalid qualifier key: {}", key)));
        }
        let value = match raw_value {
            Some(v) => decode(v)?,
            None => String::new(),
        };
        if value.is_empty() {
            continue;
        }
        out.insert(key, value);
    }
    Ok(out)
}

fn normalize_subpath(s: &str) -> Result<Option<String>, PurlError> {
    let mut segments: Vec<String> = Vec::new();
    for raw in s.split('/') {
        let segment = decode(raw)?;
        if segment.is_empty() || segment == "." || segment == ".." {
            continue;
        }
        segments.push(segment);
    }
    Ok(if segments.is_empty() {
        None
    } else {
        Some(segments.join("/"))
    })
}

fn decode(s: &str) -> Result<String, PurlError> {
    let bytes = s.as_bytes();
    let mut out: Vec<u8> = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' {
            if i + 2 >= bytes.len() {
                return Err(err(&format!("invalid percent-encoding: {}", s)));
            }
            let hi = hex_value(bytes[i + 1]).ok_or_else(|| err("invalid percent-encoding"))?;
            let lo = hex_value(bytes[i + 2]).ok_or_else(|| err("invalid percent-encoding"))?;
            out.push((hi << 4) | lo);
            i += 3;
        } else {
            out.push(bytes[i]);
            i += 1;
        }
    }
    String::from_utf8(out).map_err(|_| err("invalid utf-8 in purl component"))
}

// Percent-encode a component, leaving the unreserved set and ":" (the canonical purl form).
fn encode(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for &b in s.as_bytes() {
        let keep = b.is_ascii_alphanumeric() || matches!(b, b'-' | b'.' | b'_' | b'~' | b':');
        if keep {
            out.push(b as char);
        } else {
            out.push('%');
            out.push(hex_digit(b >> 4));
            out.push(hex_digit(b & 0x0f));
        }
    }
    out
}

fn is_valid_type(t: &str) -> bool {
    let bytes = t.as_bytes();
    if bytes.is_empty() || !bytes[0].is_ascii_alphabetic() {
        return false;
    }
    bytes
        .iter()
        .all(|&b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'+' | b'-'))
}

fn is_valid_qualifier_key(k: &str) -> bool {
    let bytes = k.as_bytes();
    !bytes.is_empty()
        && bytes
            .iter()
            .all(|&b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'-' | b'_'))
}

fn hex_value(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

fn hex_digit(n: u8) -> char {
    let n = n & 0x0f;
    if n < 10 {
        (b'0' + n) as char
    } else {
        (b'A' + (n - 10)) as char
    }
}
