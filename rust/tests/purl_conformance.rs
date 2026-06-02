// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Spice Labs, Inc. & Contributors

//! Runs the vendored purl-spec suite in ../vectors/extrinsic.json against this implementation.
//! serde_json is a dev-dependency only — it does not propagate to consumers of this crate.

use std::collections::BTreeMap;

use coordinates::purl::{self, Purl};
use serde_json::Value;

#[test]
fn purl_vectors() {
    let mut count = 0;
    for file in ["extrinsic.json", "purl-types.json", "purl-ns-rules.json"] {
        let path = format!("{}/../vectors/{}", env!("CARGO_MANIFEST_DIR"), file);
        let text = std::fs::read_to_string(&path).unwrap_or_else(|_| panic!("read {}", path));
        let doc: Value = serde_json::from_str(&text).unwrap();

        for case in doc["tests"].as_array().unwrap() {
            let test_type = case["test_type"].as_str().unwrap();
            let fail = case["expected_failure"].as_bool().unwrap_or(false);
            let input = &case["input"];
            let expected = &case["expected_output"];
            let desc = case["description"].as_str().unwrap_or("");

            match test_type {
                "parse" => {
                    let result = purl::parse(input.as_str().unwrap());
                    if fail {
                        assert!(result.is_err(), "expected parse failure: {}", desc);
                    } else {
                        let parsed = result.unwrap_or_else(|e| panic!("{}: {}", desc, e));
                        assert_components(&parsed, expected, desc);
                    }
                }
                "build" => {
                    let result = purl::build(&purl_from_json(input));
                    if fail {
                        assert!(result.is_err(), "expected build failure: {}", desc);
                    } else {
                        assert_eq!(result.unwrap(), expected.as_str().unwrap(), "{}", desc);
                    }
                }
                "roundtrip" => {
                    let result = purl::parse(input.as_str().unwrap()).and_then(|p| purl::build(&p));
                    if fail {
                        assert!(result.is_err(), "expected roundtrip failure: {}", desc);
                    } else {
                        assert_eq!(result.unwrap(), expected.as_str().unwrap(), "{}", desc);
                    }
                }
                other => panic!("unknown test_type: {}", other),
            }
            count += 1;
        }
    }
    assert_eq!(count, 552, "expected 552 purl cases");
}

fn purl_from_json(c: &Value) -> Purl {
    let mut qualifiers = BTreeMap::new();
    if let Some(obj) = c["qualifiers"].as_object() {
        for (k, v) in obj {
            if let Some(s) = v.as_str() {
                qualifiers.insert(k.clone(), s.to_string());
            }
        }
    }
    Purl {
        r#type: c["type"].as_str().unwrap_or("").to_string(),
        namespace: c["namespace"].as_str().map(str::to_string),
        name: c["name"].as_str().unwrap_or("").to_string(),
        version: c["version"].as_str().map(str::to_string),
        qualifiers,
        subpath: c["subpath"].as_str().map(str::to_string),
    }
}

fn assert_components(p: &Purl, expected: &Value, desc: &str) {
    assert_eq!(
        p.r#type.as_str(),
        expected["type"].as_str().unwrap(),
        "type: {}",
        desc
    );
    assert_eq!(
        p.namespace.as_deref(),
        expected["namespace"].as_str(),
        "namespace: {}",
        desc
    );
    assert_eq!(
        p.name.as_str(),
        expected["name"].as_str().unwrap(),
        "name: {}",
        desc
    );
    assert_eq!(
        p.version.as_deref(),
        expected["version"].as_str(),
        "version: {}",
        desc
    );
    assert_eq!(
        p.subpath.as_deref(),
        expected["subpath"].as_str(),
        "subpath: {}",
        desc
    );

    let mut expected_qualifiers = BTreeMap::new();
    if let Some(obj) = expected["qualifiers"].as_object() {
        for (k, v) in obj {
            if let Some(s) = v.as_str() {
                expected_qualifiers.insert(k.clone(), s.to_string());
            }
        }
    }
    assert_eq!(p.qualifiers, expected_qualifiers, "qualifiers: {}", desc);
}
