# Spec 005: Admin & Observability Console

## Summary

Every RelayHub feature built so far (Specs 001-004) is only reachable via REST API or `curl`/scripts
— there is no screen. This spec gives RelayHub itself a web UI: Source/Target/Subscription
management and Delivery/DLQ observability, served by RelayHub (not by the separate
`developer.cleanbrain.me` site, which will *consume* RelayHub's API/Observability surface to show
its own view — this spec is about RelayHub having a first-class UI of its own, independent of that).

Split into a public, read-only half (Observability) and an authenticated, read-write half
(Source/Target/Subscription management) — RelayHub's API currently has no authentication at all,
and a public unauthenticated "create/delete Source" form would be a real security problem.

## In scope

- **React SPA** (`frontend/`, separate build/toolchain, same repo) built to static assets and
  served by the existing Spring Boot app (`src/main/resources/static/`, SPA fallback route to
  `index.html` for any non-API, non-actuator, non-ingress path) — one Docker image, one
  Deployment, no new Kubernetes Service/HTTPRoute needed.
- **Spring Security**, HTTP Basic, a single fixed admin account (`ADMIN_USERNAME`/`ADMIN_PASSWORD`
  env vars, no user table/registration — personal-project scale). Protects only the
  write/mutating endpoints listed below; every existing endpoint's current behavior (including
  `/ingress/v1/**`, which is Source-system-facing, not admin-facing, and must stay unauthenticated)
  is otherwise unchanged.
  - Protected (auth required): every `POST`/`PUT`/`DELETE` under `/api/sources`, `/api/targets`,
    `/api/subscriptions` (including nested `/api/sources/{sourceKey}/events`), plus
    `POST /api/deliveries/{id}/replay`.
  - Public (unchanged): every `GET`, plus all of `/ingress/v1/**` and `/actuator/**`.
- **New read endpoints** needed for list views that don't exist yet: `GET /api/sources`,
  `GET /api/targets`, `GET /api/subscriptions` (each returning the full set including `INACTIVE`
  ones — the UI distinguishes by status badge rather than hiding deleted rows — no pagination yet,
  matching this project's personal-project data volume).
- **New write endpoints** for edit/delete, none of which exist today (Source/Target/Subscription
  currently support only create + get-by-key):
  - `PUT /api/sources/{key}`, `PUT /api/targets/{key}`, `PUT /api/subscriptions/{id}` — update the
    mutable fields (name, description, and type-specific fields: `Target.baseUrl`/
    `authenticationConfig`, `Subscription.targetMethod`/`targetPath`/`targetPayloadTemplate`/
    `retryPolicy`). `key` itself is immutable (it's load-bearing in Ingress URLs and existing
    Subscriptions) — not part of the edit form.
  - `DELETE /api/sources/{key}`, `DELETE /api/targets/{key}`, `DELETE /api/subscriptions/{id}` —
    **soft delete**: flips `status` to `INACTIVE`, matching the `Status` enum already on all three
    entities and the existing `findActive*` query pattern (`SubscriptionService.findActiveForSourceEvent`
    already filters on it). No hard delete — these rows are referenced by Delivery/Event history
    that must stay intact, and Ingress/delivery logic already treats `INACTIVE` as "not in effect."
  - `PUT /api/sources/{sourceKey}/events/{key}`, `DELETE /api/sources/{sourceKey}/events/{key}` —
    same treatment for SourceEvent, needed since Sources are edited/managed alongside their events
    in the UI.
- **Observability pages** (public): dashboard summary (counts by Delivery state, recent
  ingress/outbox/delivery activity — reusing `/actuator/prometheus` data or dedicated summary
  endpoints, whichever proves simpler once implementation starts), Delivery list filterable by
  state (so `DEAD` is easy to find), Delivery detail showing its attempts.
- **Management pages** (authenticated): Source/Target/Subscription list + detail (read side reuses
  the public list/get endpoints); create, edit, and delete (deactivate) forms/actions for each,
  backed by the endpoints above; a Replay button on a `DEAD` Delivery's detail page.

## Deliberately out of scope

- Hard delete of a Source/Target/Subscription/SourceEvent — soft delete (status -> `INACTIVE`)
  only, since Delivery/Event history references these rows.
- Multi-user accounts, roles, or a login/registration flow — one fixed admin credential is enough
  at this scale.
- Anything on the separate `developer.cleanbrain.me` site — that's a different, not-yet-started
  project that will call this console's same public API/Observability surface from the outside.
- Real-time updates (WebSocket/SSE push) — polling on a page refresh or a simple interval is
  enough; no live-streaming dashboard.
- Pagination, search, or filtering beyond "filter Deliveries by state" — data volume at this scale
  doesn't need it yet.

## Acceptance

- `GET /api/sources`, `/api/targets`, `/api/subscriptions` exist and return the current live data
  (verified against the already-deployed `demo-flightstatus` scenario, not just a test fixture).
- Any protected `POST`/`PUT`/`DELETE` without credentials returns `401`; with the correct
  `ADMIN_USERNAME`/`ADMIN_PASSWORD` it succeeds. A `DELETE` on a Source/Target/Subscription/
  SourceEvent leaves the row in the database with `status=INACTIVE`, not removed — verified
  directly, not just assumed from the code.
- `/ingress/v1/**` and every existing `GET` endpoint's behavior is unchanged — verified by
  re-running the existing test suite plus a manual check against the live deployment.
- The SPA is reachable at `/` on both a local `bootRun` and the live
  `relayhub-java.developer.cleanbrain.me` deployment (real redeploy, not just a local build) —
  the current Whitelabel/404 root response is what this spec replaces.
- From the deployed UI: an operator can view the current Sources/Targets/Subscriptions, see the
  `demo-travelapp-vendor` DLQ entries relayhub-demo-systems is already generating, replay one of
  them, and edit/deactivate a Source/Target — logged in through the browser, not just via `curl`.
