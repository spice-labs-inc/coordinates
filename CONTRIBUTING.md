## Contributing to This Project

Thank you for your interest in this project. We welcome all kinds of contributions: code, documentation, bug reports, feature requests, and more. This guide will help you get started. We ask that you engage in good faith, honesty, and integrity, and respect that the maintainers make the final decisions on this project.

---

## Reporting Bugs

Please include:

- A clear, descriptive title
- Steps to reproduce the issue
- What you expected to happen vs. what actually happened
- Any relevant logs, vectors, or files
- Your environment (OS, language/toolchain version, how it was installed, etc.)

Open a [new issue](../../issues/new) to report the problem.

---

## Suggesting Features

Please include:

- A summary of the problem you are trying to solve
- Why it is important or useful
- A rough idea of how it could be implemented

Open a [new issue](../../issues/new) to make the suggestion.

---

## Making Code Contributions

This repository is contract-first: `spec.yaml` defines the identifiers and `vectors/` proves them. Please keep that in mind:

- Changes to an identifier's behavior start in `spec.yaml` and the vectors, not in a single implementation
- Every implementation must pass the shared vectors — add or update vectors when you change behavior
- Follow the coding style used in each implementation; linters and formatters are configured, so use them
- Run the existing tests to ensure everything still passes
- Write clear commit messages that describe the changes:

  ```bash
  git commit -m "Add blake3 to the intrinsic identifier set"
  git push origin add/blake3
  ```

- Keep commits focused. Two smaller commits for unrelated changes beat one combined commit titled "updates"

---

## Tests and CI

- CI runs every implementation against the shared vectors, and checks the committed vectors against the spec
- Locally, `make test` runs the lot at once — the vectors check plus every implementation; `make ts`, `make java`, and `make rust` run one at a time. It needs the per-language toolchains installed (pnpm, a JDK, and cargo); each implementation's README also shows how to run it directly
- Run the tests and linters against your changes before opening a pull request
- Reach out if you are unsure how to run them

---

## Opening a Pull Request

- Open your pull request against the `next` branch. We integrate changes into `next` before cutting releases to `main` and creating tagged releases
- Use the pull request template as a guide. Explain why you are making changes as clearly as you can
- Link to any related issues
- Be ready to discuss or make changes after review
- Ensure CI passes on your PR and make any required changes

---

## Licensing

All contributions must be compatible with the project's [license](LICENSE), and you must have the legal right to contribute them. By submitting code, you agree to license it under the same terms.

---

## Thank You!

Thank you for contributing. Whether you are fixing a typo, suggesting a feature, or rewriting a core component, your contribution helps and is greatly appreciated.

For questions, feel free to open an issue or start a discussion. Community discussions for Spice Labs open source projects are on Matrix at https://matrix.to/#/#spice-labs:matrix.org

---
