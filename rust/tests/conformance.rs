// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Spice Labs, Inc. & Contributors

// Runs the shared vectors in ../../vectors/intrinsic.json against this implementation. serde_json is
// a dev-dependency only — it does not propagate to anything that depends on this crate.

use std::collections::HashMap;

use serde_json::Value;

fn from_hex(s: &str) -> Vec<u8> {
    (0..s.len() / 2)
        .map(|i| u8::from_str_radix(&s[i * 2..i * 2 + 2], 16).unwrap())
        .collect()
}

#[test]
fn intrinsic_vectors() {
    let text = std::fs::read_to_string(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../vectors/intrinsic.json"
    ))
    .unwrap();
    let doc: Value = serde_json::from_str(&text).unwrap();
    let mut checked = 0;
    for v in doc["vectors"].as_array().unwrap() {
        let name = v["name"].as_str().unwrap();
        let input = from_hex(v["input_hex"].as_str().unwrap());
        let got: HashMap<&str, String> = coordinates::intrinsic(&input).into_iter().collect();
        for (id, expected) in v["expect"].as_object().unwrap() {
            assert_eq!(
                got.get(id.as_str()).unwrap(),
                expected.as_str().unwrap(),
                "{} for input \"{}\"",
                id,
                name
            );
            checked += 1;
        }
    }
    assert!(checked > 0, "no vectors checked");
    eprintln!("checked {} (identifier, vector) pairs", checked);
}
