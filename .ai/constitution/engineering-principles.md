# Engineering Principles

This document defines engineering principles that should outlive a technology choice or an individual feature. Adopted from Agent Development Starter and reviewed for RelayHub; extended with principles specific to this project's design (see "RelayHub-specific principles" below).

**Status: accepted** (maintainer review, 2026-09-10). Confirmed as-is, without amendment, after being exercised in practice across Spec 001 and Spec 002 — e.g. ADR-0001/0002/0003 for explicit architecture, real H2 and manual Postgres verification (not just compilation) for verifiable outcomes, and the Spec 002 scope split for minimal coherent change.

## Evidence before change

Inspect existing documentation and implementation first. Prefer repository evidence over assumptions, and do not propose changes based on a structure that has not been verified.

## Minimal, coherent change

Prefer the smallest coherent change that satisfies the requirement. Do not add speculative abstractions, features, or automation.

## Explicit architecture

Do not change architectural boundaries or conventions silently. Surface decisions that have long-term impact or are difficult to reverse, and record them in an ADR before or with implementation.

## Verifiable outcomes

Produce outcomes that can be verified. Run automated checks when they exist; otherwise state the verification method and its limitations. Distinguish guidance that requires judgment from rules that should be enforced by code, tests, linters, or CI.

## Agent-agnostic core

Do not bind product intent, architecture, decisions, or engineering principles to an agent-specific instruction file. An agent adapter may contain only the differences required to route its agent to shared sources.

## Separated boundaries

Separate domain concerns from external systems and tool integrations. See `docs/architecture/system-design.md` for RelayHub's concrete module boundaries.

## RelayHub-specific principles

Sourced directly from the accepted RelayHub design context (2026-09-10):

- **External contract flexibility over a RelayHub envelope.** Source and Target systems keep their existing payload contracts; RelayHub never requires them to adopt a RelayHub-specific envelope or metadata fields.
- **Reliability over feature count.** Prefer fewer features that are dependable over more features that are not.
- **Observable failure over hidden failure.** Failures must be visible and recoverable (retry, DLQ, replay, audit), never silently dropped.
- **Replayable structure over manual correction.** Favor a replay-capable delivery pipeline over ad hoc manual database correction.
- **Real integration over mock-only code.** Prefer tests and demos grounded in real (or realistically faked, e.g. WireMock/Testcontainers) integrations over Source/Target-specific mock code paths.
- **Modular monolith before distribution.** Start as a modular monolith with clear internal module boundaries; introduce distributed complexity (e.g. splitting services) only after it is proven necessary.
- **Working software over long speculative design.** A feature is not done until tests and Docker verification pass; do not build abstractions ahead of a vertical slice proving they are needed.
