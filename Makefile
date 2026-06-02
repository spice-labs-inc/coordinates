# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 Spice Labs, Inc. & Contributors
#
# Convenience runner that mirrors the CI jobs. Each implementation also builds, tests, and lints on
# its own; nothing here is required to use the repo. Commands match .github/workflows.

.PHONY: check test lint fmt vectors ts java rust build clean

# Everything CI enforces: formatting + lint + zero warnings, then conformance.
check: lint test

# Prove every implementation reproduces the shared vectors.
test: vectors ts java rust

# Static checks only — no formatting changes, zero tolerance for warnings.
lint:
	pnpm install
	pnpm run format:check
	pnpm -C ts install
	pnpm -C ts run typecheck
	cargo fmt --manifest-path rust/Cargo.toml --all -- --check
	cargo clippy --manifest-path rust/Cargo.toml --all-targets -- -D warnings
	mvn -B -ntp -f java/pom.xml spotless:check

# Auto-format every language + the vectors/docs.
fmt:
	pnpm install
	pnpm run format
	cargo fmt --manifest-path rust/Cargo.toml --all
	mvn -B -ntp -f java/pom.xml spotless:apply

# Validate the vectors are well-formed before anything consumes them.
vectors:
	node vectors/validate.mjs

ts:
	pnpm -C ts install
	pnpm -C ts test

java:
	mvn -B -ntp -f java/pom.xml test

rust:
	cargo test --manifest-path rust/Cargo.toml

build:
	pnpm -C ts run build
	mvn -B -ntp -f java/pom.xml package
	cargo build --manifest-path rust/Cargo.toml --release

clean:
	rm -rf ts/dist ts/node_modules node_modules
	mvn -B -ntp -f java/pom.xml clean
	cargo clean --manifest-path rust/Cargo.toml
