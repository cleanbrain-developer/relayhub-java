> 이 문서는 [`engineering-principles.md`](engineering-principles.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Engineering Principles

이 문서는 특정 technology 선택이나 개별 feature보다 오래 지속되어야 할 engineering principle을 정의합니다. Agent Development Starter로부터 채택되어 RelayHub를 위해 검토되었으며, 이 project 고유의 design에 특화된 principle로 확장되었습니다 (아래 "RelayHub-specific principles" 참고).

**Status: accepted** (maintainer review, 2026-09-10). Spec 001과 Spec 002 전반에 걸쳐 실제로 적용된 뒤 수정 없이 그대로 확정되었습니다 — 예를 들어 explicit architecture를 위한 ADR-0001/0002/0003, verifiable outcome을 위한 실제 H2 및 수동 Postgres 검증(단순 compilation이 아닌), minimal coherent change를 위한 Spec 002 scope 분리 등입니다.

## Evidence before change

먼저 기존 documentation과 implementation을 검토하세요. assumption보다 repository evidence를 우선하고, 검증되지 않은 구조를 근거로 변경을 제안하지 마세요.

## Minimal, coherent change

requirement를 충족하는 가장 작은 coherent change를 선호하세요. speculative한 abstraction, feature, automation을 추가하지 마세요.

## Explicit architecture

architectural boundary나 convention을 조용히 변경하지 마세요. 장기적인 영향을 미치거나 되돌리기 어려운 decision을 드러내고, implementation 이전 또는 함께 ADR로 기록하세요.

## Verifiable outcomes

검증 가능한 outcome을 만드세요. automated check가 있으면 실행하고, 없으면 verification method와 그 한계를 명시하세요. judgment가 필요한 guidance와 code, test, linter, CI로 강제되어야 하는 rule을 구분하세요.

## Agent-agnostic core

product intent, architecture, decision, engineering principle을 agent-specific instruction file에 묶지 마세요. agent adapter는 공유 source로 해당 agent를 안내하는 데 필요한 차이점만 포함할 수 있습니다.

## Separated boundaries

domain concern을 external system 및 tool integration과 분리하세요. RelayHub의 구체적인 module boundary는 `docs/architecture/system-design.md`를 참고하세요.

## RelayHub-specific principles

accepted RelayHub design context(2026-09-10)에서 직접 가져온 원칙입니다:

- **RelayHub envelope보다 external contract flexibility.** Source와 Target system은 기존 payload contract를 그대로 유지하며, RelayHub는 이들이 RelayHub 전용 envelope나 metadata field를 채택하도록 요구하지 않습니다.
- **feature 수보다 reliability.** 더 많지만 신뢰할 수 없는 feature보다, 적더라도 신뢰할 수 있는 feature를 선호합니다.
- **숨겨진 failure보다 observable failure.** failure는 눈에 보이고 복구 가능해야 하며(retry, DLQ, replay, audit), 조용히 버려져서는 안 됩니다.
- **수동 correction보다 replayable structure.** ad hoc한 수동 database correction보다 replay가 가능한 delivery pipeline을 선호합니다.
- **mock 전용 code보다 real integration.** Source/Target 전용 mock code path보다 실제(또는 WireMock/Testcontainers처럼 현실적으로 가짜인) integration에 기반한 test와 demo를 선호합니다.
- **distribution 이전에 modular monolith.** 명확한 internal module boundary를 가진 modular monolith로 시작하고, distributed complexity(예: service 분리)는 필요성이 입증된 이후에만 도입합니다.
- **오래 지속되는 speculative design보다 working software.** test와 Docker verification을 통과하기 전까지 feature는 완료된 것이 아니며, vertical slice가 필요성을 입증하기 전에 abstraction을 먼저 만들지 않습니다.
