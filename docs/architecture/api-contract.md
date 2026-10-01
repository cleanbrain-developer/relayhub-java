# API Contract (OpenAPI)

The REST API contract is generated at runtime from the actual `@RestController`/record-DTO code
(springdoc-openapi), not hand-maintained — self-review finding, 2026-10-02: `system-design.md`'s
stack table always said `Contract: OpenAPI / JSON Schema`, but no OpenAPI generator was actually
wired up until now, leaving no machine-checkable API contract. This matters specifically for the
stated multi-language-sibling plan (`relayhub-node`, `relayhub-go`, ... — see
`docs/decisions/ADR-0002-java-spring-boot-stack.md`): a generated, always-in-sync contract is what
a sibling implementation should be checked against, not a hand-read of this repo's Java source.

## Where to get it

- **Browsable UI**: `/swagger-ui.html` (local: `http://localhost:8080/swagger-ui.html`; production:
  `https://relayhub-java.developer.cleanbrain.me/swagger-ui.html` — public, same as every other GET
  endpoint in this deployment, see `security/SecurityConfig.java`).
- **Raw spec** (for codegen or diffing): `GET /v3/api-docs`.

Deliberately **not committed as a static file in this repository** — a checked-in snapshot would
immediately start drifting the moment any controller or DTO changes, which is exactly the kind of
staleness this document exists to avoid (see `system-design.md`'s now-corrected stack table for a
real example of that happening). Fetch it live from a running instance (local or production) when
you need it.

## What it covers

Every `@RestController` under `/api/**`, generated from the real request/response record DTOs —
field names, types, and enum values are exactly what the running code actually accepts and returns
(verified 2026-10-02: 31 paths, spot-checked against `SubscriptionResponse`'s real shape including
`mappingWarnings`, the most recently added field at the time).

## Known limitation: `/ingress/v1/**`

The Ingress endpoint (`IngressController`) is a single Spring `@RequestMapping(value =
"/ingress/v1/**")` handling every registered Source Event dynamically — the real path for any given
Source Event is generated from registration data (`Source.key` + `SourceEvent.key`), not fixed at
compile time. OpenAPI has no way to represent "the path is whatever's currently registered," so this
renders as the literal, non-useful `/ingress/v1/**` entry in the generated spec. For the real
convention (method mapping, path shape, payload rules), see `system-design.md`'s "Ingress URL
convention" section and `domain-model.md`'s "Event delivery flow" diagram instead — and query
`GET /api/sources/{sourceKey}/events` for a specific deployment's actual, currently-registered
ingress paths (each `SourceEvent.ingressPath`/`ingressMethod`).

## Authentication

`/api/**` writes (`POST`/`PUT`/`DELETE`) require HTTP Basic with the admin role; everything else
(`GET` across the board, `/ingress/v1/**`, `/actuator/**`) is public — see
`security/SecurityConfig.java`. The generated spec declares this as a single `basicAuth` security
scheme applied globally; it does not yet distinguish GET-is-public from write-is-admin-only
per-operation (a cosmetic gap in the generated spec, not in the real enforcement, which is accurate
and tested — see `AdminConsoleApiTest`).
