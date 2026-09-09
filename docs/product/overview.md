# Product Overview

## Product

RelayHub (Java) is an event-driven data integration platform. It receives data-change events from Source systems, standardizes them internally into a canonical event model, and delivers them to Target systems in each Target's existing API contract — including retry, replay, audit, and observability.

## Core principle

Source and Target systems are never required to adopt a RelayHub-specific payload contract. Each keeps its existing payload shape; RelayHub does the interpretation, standardization, and transformation in between.

## Problem

- Point-to-point direct API calls between systems create tight coupling.
- Small changes trigger full `DELETE`+`INSERT` or full `UPSERT` instead of a targeted update.
- Failed deliveries have no retry, hook, or alert, so failure states go unnoticed for long periods.
- Integration logic is duplicated per system pair, increasing operational complexity.
- Lack of delivery history and a replay mechanism forces manual database correction.
- Source and Target systems become tightly coupled to each other's API structure.

RelayHub absorbs this complexity centrally instead of leaving each system pair to solve it independently.

## Product thesis

RelayHub receives data-change events from a Source system, standardizes them internally, transforms them to match each Target's existing API contract, and delivers them reliably — including retry, replay, audit, and observability.

## Users

- **Source system owners/developers** — systems that emit data-change events. They call a per-event Ingress URL with their existing payload; they are never asked to adopt a RelayHub envelope.
- **Target system owners** — systems that receive transformed events through their own existing API contract.
- **Operators** — the people who register Source/Source Event/Target/Subscription definitions, monitor delivery history, and trigger manual replay when a delivery lands in the dead-letter queue.

## V1 experience (first vertical slice)

1. `demo-source` sends a `customer-created` payload to its generated Ingress URL.
2. RelayHub identifies the Source/Event from the URL's registration and standardizes the payload into a canonical event using JSONPath extraction rules, then stores it.
3. Two Subscriptions (Target A, Target B) each map and deliver the event.
4. Target A succeeds; Target B fails (intentionally, for the scenario).
5. Target B is retried, then moved to the dead-letter queue (DLQ).
6. An Operator triggers Replay; Target B succeeds.
7. The Event and every Delivery Attempt are queryable end-to-end.

See `docs/product/goals.md` ("Success criteria") for how this maps to acceptance criteria, and `specs/001-push-event-delivery/` for the full specification.
