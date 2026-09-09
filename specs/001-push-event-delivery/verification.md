# Spec 001: Verification

## Definition of done (from `.ai/constitution/agent-behavior.md` and `docs/product/goals.md`)

- `./gradlew test` passes.
- `docker compose up` starts cleanly.
- Integration/contract tests pass.
- The acceptance scenario in `spec.md` is reproducible end-to-end (e.g. via a `scripts/compose-smoke-test.sh` once it exists).
- Related design/status documentation is updated in the same change (`docs/status/current-state.md` at minimum).

Compiling code is not sufficient evidence of completion — see `.ai/constitution/agent-behavior.md`.
