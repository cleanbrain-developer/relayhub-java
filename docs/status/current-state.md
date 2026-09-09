# Current State

Last updated: 2026-09-10

## Current phase

`v1-foundation` — establish the self-describing repository foundation (identity, constitution, product, architecture, decisions, working state) before implementation begins. No application code exists yet.

## Completed

- Captured RelayHub's product/architecture design from the accepted design context (2026-09-10) and persisted it into `docs/product/`, `docs/architecture/system-design.md`, and `docs/decisions/`, so the original external documents are no longer a required dependency.
- Adopted the Agent Development Starter foundation (`PROJECT.yaml`, `CLAUDE.md`, `.ai/constitution/`, `docs/architecture/agent-context-model.md`, `docs/architecture/repository-structure.md`, ADR-0001), adapted for this project (Claude Code only; no `docs/guides/` yet).
- Decided and recorded the Java 21/Spring Boot core stack decision, including the naming convention for future same-product, different-language repositories (ADR-0002).
- Created the `specs/001-push-event-delivery/` skeleton for the first vertical slice.
- Confirmed target naming with the maintainer: GitHub repository `relayhub-java` (public), planned hostname `relayhub-java.developer.cleanbrain.me`, planned Kubernetes namespace `cleanbrain-me-relayhub-java` (not yet created — see Open decisions).

## In progress

- None. See Next.

## Next

1. Maintainer reviews `.ai/constitution/` (currently drafted, not yet confirmed — see `docs/decisions` policy that constitution content requires explicit maintainer review before being treated as binding).
2. `git init` this repository, create the `relayhub-java` GitHub repository (public), and perform the first push — each requires the maintainer's explicit confirmation before being carried out (not yet done).
3. Run the bootstrap acceptance test (fresh Claude Code session reading only `CLAUDE.md`) before treating the foundation as complete.
4. Commit the adopted foundation as a reviewable baseline once the acceptance test passes.
5. Begin Phase 1 implementation: scaffold `build.gradle.kts`/`settings.gradle.kts`, `docker-compose.yml`, and the `source`/`sourceevent`/`target`/`subscription`/`ingress`/`event`/`delivery`/`mapping` modules, then implement the first vertical slice per `specs/001-push-event-delivery/`.
6. Revisit `PROJECT.yaml`'s `delivery.implementation_present` and `current_phase`/`lifecycle` once Phase 1's first implementation lands — do not leave them stale.
7. Decide whether/when to add this service to `cleanbrain-me-infra` (Kubernetes namespace `cleanbrain-me-relayhub-java`, Gateway listener for `relayhub-java.developer.cleanbrain.me`, `*.developer.cleanbrain.me` wildcard DNS record) — V1 targets local Docker Compose only, so this is not near-term.

## Open decisions

- Constitution content in `.ai/constitution/` is drafted, not yet maintainer-reviewed (ADS adoption step 4, human input required).
- Whether `docs/guides/` is ever needed for this repository (currently omitted; no guide-worthy repeatable procedure exists yet).
- Whether a `relayhub-node` (or other language) sibling repository will actually be built, and when.
- Exact CI/CD model for this repository — not yet designed; `cleanbrain-me-infra`'s SSH/`kubectl set image` model is not necessarily reusable as-is since this service does not yet have a production deployment target.

## Known constraints

- No application code, build system, test suite, or CI exists yet.
- The original design conversation and exported documents were used only as bootstrap evidence for this foundation and are not required context for future sessions — see ADR-0001.
- This repository does not own Kubernetes manifests; `cleanbrain-me-infra` does, if/when this service is deployed there.

## V1-foundation exit criteria

- A new Claude Code session, given only `CLAUDE.md`, accurately recovers RelayHub's purpose, principles, architecture, current state, and next work — see `docs/product/goals.md` ("Success criteria").
- All durable evidence for that answer exists in this repository, not in the original design conversation.
- The maintainer has reviewed and accepted (or amended) `.ai/constitution/`.
- The foundation is committed to Git as a reviewable baseline before Phase 1 implementation begins.
