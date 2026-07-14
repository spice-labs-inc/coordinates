// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Spice Labs, Inc. & Contributors

//! Tests for `Purl::to_maven_url`, which leaves `+` unencoded in Maven versions.

use coordinates::purl;

#[test]
fn maven_version_with_plus_is_literal() {
    let p = purl::parse("pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7").unwrap();
    assert_eq!(
        p.to_maven_url().unwrap(),
        "pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7"
    );
}

#[test]
fn maven_version_with_plus_and_qualifier() {
    let p =
        purl::parse("pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7?classifier=sources")
            .unwrap();
    assert_eq!(
        p.to_maven_url().unwrap(),
        "pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7?classifier=sources"
    );
}

#[test]
fn maven_version_without_plus_is_unchanged() {
    let p = purl::parse("pkg:maven/org.apache.commons/io@1.0.0").unwrap();
    assert_eq!(
        p.to_maven_url().unwrap(),
        "pkg:maven/org.apache.commons/io@1.0.0"
    );
}

#[test]
fn non_maven_version_with_plus_is_still_encoded() {
    let p = purl::parse("pkg:deb/debian/attr@1:2.4.47-2+b1").unwrap();
    assert_eq!(
        p.to_maven_url().unwrap(),
        "pkg:deb/debian/attr@1:2.4.47-2%2Bb1"
    );
}

#[test]
fn maven_name_with_plus_is_still_encoded() {
    let p = purl::parse("pkg:maven/g/art+ifact@1.0").unwrap();
    assert_eq!(p.to_maven_url().unwrap(), "pkg:maven/g/art%2Bifact@1.0");
}

#[test]
fn maven_qualifier_value_with_plus_is_still_encoded() {
    let p = purl::parse("pkg:maven/g/a@1.0?foo=a+b").unwrap();
    assert_eq!(p.to_maven_url().unwrap(), "pkg:maven/g/a@1.0?foo=a%2Bb");
}

#[test]
fn maven_version_with_other_unsafe_characters_still_encodes() {
    let p = purl::parse("pkg:maven/g/a@1.0 0").unwrap();
    assert_eq!(p.to_maven_url().unwrap(), "pkg:maven/g/a@1.0%200");
}

#[test]
fn maven_version_with_plus_roundtrips() {
    let original = purl::parse("pkg:maven/net.fabricmc/sponge-mixin@0.15.4+mixin.0.8.7").unwrap();
    let roundtripped = purl::parse(&original.to_maven_url().unwrap()).unwrap();
    assert_eq!(original.version, roundtripped.version);
}
