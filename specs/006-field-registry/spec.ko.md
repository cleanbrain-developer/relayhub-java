> 이 문서는 [`spec.md`](spec.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 006: Source/Target Field Registry (proposal — not yet implemented)

## Problem

Subscription 생성/편집(`MappingBuilder`, Spec 005 follow-up)은 mapping *UI*는 개선했지만
그 아래의 데이터는 개선하지 못했습니다: Subscription의 `targetPayloadTemplate` mapping row는
여전히 free text입니다 — operator는 정확한 source JSONPath(`$.customerNo`)와 정확한 target
field 이름(`dealerId`)을 기억이나 외부 문서를 통해 이미 알고 있어야 합니다. Source Event가
실제로 무엇을 emit하는지, Target이 실제로 무엇을 받아들이는지에 대해 mapping을 검증하는 것도
없고, 같은 Source Event나 Target을 향한 다른 Subscription 사이에서 재사용 가능한 것도 없습니다.

Maintainer 본인의 프레이밍: Source와 Target은 각각 자신이 제공하거나 필요로 하는 *모든 field*를
등록해야 하며, Subscription mapping은 free-typed text 대신 이 두 registry로부터의 선택
(dropdown)이 되어야 합니다.

## Proposed design

### Two new entities

**`SourceField`** — SourceEvent의 payload가 제공할 수 있는 field 하나당 한 row입니다. 단순
Source가 아니라 **SourceEvent**에 scoping됩니다: 같은 Source의 두 event(예:
`flight-created`와 `flight-status-updated`)는 서로 다른 payload 형태를 가질 수 있고, field의
JSONPath는 오직 하나의 event의 payload에 상대적으로만 의미가 있습니다.

**`TargetField`** — Target의 payload가 받아들일 수 있는 field 하나당 한 row입니다. v1에서는
per-endpoint/path가 아니라 **Target** 자체에 scoping됩니다 — Subscription은 이미 mapping마다
자신만의 `targetPath`를 가지고 있으며, 그 위에 별도의 "Target endpoint" 개념을 도입하는 것은
이 spec이 지금 필요로 하지 않는 범위처럼 느껴졌습니다. 만약 Target의 서로 다른 endpoint가
정말로 서로 다른 field set을 필요로 한다면, 이 registry를 이미 사용 중인 Subscription을
건드리지 않고 나중에 분리할 수 있습니다.

### Proposed metadata per field (the part asking for feedback)

| Field | Type | Notes |
|---|---|---|
| `key` | string, required | mapping UI에 표시되는 짧은 field 이름(예: `customerNo`, `dealerId`) |
| `jsonPath` | string, **SourceField only**, required | 원본 Source payload에 대한 추출 경로(예: `$.customerNo`) |
| `dataType` | enum: `STRING`/`NUMBER`/`BOOLEAN`/`OBJECT`/`ARRAY`/`DATE` | UI hint와 가벼운 validation용 — 완전한 JSON Schema 대체는 아님 |
| `description` | string, optional | 사람이 읽을 수 있는 설명 |
| `exampleValue` | string, optional | operator가 Source 자체의 문서를 읽지 않고도 field를 알아볼 수 있도록 UI에 보여주는 예시 값 |
| `required` | boolean | SourceField: "Source가 항상 이것을 보낸다." TargetField: "Target이 이것 없이는 request를 거부한다." |
| `sensitive` | boolean | PII/secret 표시자 — 아직 동작은 없음(masking/redaction 미구현)이지만, 나중에 그런 기능을 만들 때 migration이 필요 없도록 지금 캡처해둘 가치가 있음 |
| `status` | enum: `ACTIVE`/`INACTIVE` | 여기 있는 다른 모든 entity와 동일한 soft-delete convention |
| `createdAt` | timestamp | 표준 |

Open question for feedback: 이것이 올바른 field 집합인가? 원하지 않는 한 의도적으로 제외한
후보들: `TargetField`를 위한 `defaultValue`(아무것도 mapping되지 않았을 때 보낼 값), 그리고
Source 쪽의 "이 field는 흔히 occurredAtPath/idempotencyKeyPath로 쓰인다"는 hint flag —
둘 다 첫 버전에는 시기상조의 복잡함으로 느껴졌습니다.

### Subscription mapping becomes a selection, not free text

`MappingBuilder`의 row 기반 editor는 같은 형태를 유지하지만, 각 row는 이제: 왼쪽에서는
(선택된 Target의 registry로부터) `TargetField`를 고르고, 오른쪽에서는(선택된 SourceEvent의
registry로부터) `SourceField`를 고르거나 — 아직 등록되지 않은 것에 대해서는 literal value /
raw JSONPath로 fallback합니다(`MappingBuilder`가 이미 가지고 있는 것과 동일한 "advanced"
escape hatch). submit 시 frontend는 여전히 backend가 이미 기대하는 것과 정확히 동일한
`targetPayloadTemplate` JSON 문자열을 생성합니다
(`{"<targetField.key>": "${<sourceField.jsonPath>}"}`) — **`MappingService`,
`DeliveryService`, Subscription API의 request/response shape에는 변경이 없습니다.** 이것은
additive합니다: 새로운 mapping engine이 아니라 같은 UI를 위한 더 똑똑한 데이터 원천입니다.

### New endpoints (all following the existing list/create/edit/soft-delete pattern from Spec 005)

- `GET/POST/PUT/DELETE /api/sources/{sourceKey}/events/{eventKey}/fields(/{fieldKey})`
- `GET/POST/PUT/DELETE /api/targets/{targetKey}/fields(/{fieldKey})`

둘 다 오늘날 `SourceEvent`가 `Source` 아래에 nesting되는 것과 같은 방식으로 nesting됩니다.
Write는 기존 admin auth boundary로 보호되고, read는 다른 모든 것과 마찬가지로 공개됩니다.

## Deliberately out of scope (for now)

- Subscription의 mapping이 등록된 field만 사용하도록 강제/검증하는 것 — registry는 hard
  constraint가 아니라 documentation/autocomplete 보조 수단으로 시작합니다. (나중에 그렇게 될
  수도 있습니다.) 이 결정을 scope에서 제외함으로써 field registry보다 먼저 존재하는 기존의
  seeded demo Subscription에 대한 migration 문제도 피할 수 있습니다.
  - 실제로 관측된 Source payload로부터 registry를 자동으로 채우는 것(schema inference) —
  v1에서는 operator가 손으로 field를 등록하며, 지금의 free-text 방식과 동일한 노력 수준이지만
  이후에는 재사용 가능합니다.
- Per-endpoint `TargetField` scoping ("TargetField" 항목 참고).
- `sensitive` flag를 저장하는 것 이상의 어떤 것.

## Status

구현되어 배포됨(2026-09-12). Backend: `SourceField`/`TargetField` entity,
`V2__field_registry.sql`, `/api/sources/{sourceKey}/events/{eventKey}/fields`와
`/api/targets/{targetKey}/fields` 아래의 nested REST controller들이, 여기 있는 다른 모든
entity와 동일한 list/create/edit/soft-delete(+admin hard-delete) contract를 따릅니다.
Frontend: `FieldRegistryEditor`(add/edit/delete 전체)가 Sources/Targets 카드의 "Field
mapping" toggle을 통해 노출되며, `MappingBuilder`의 양쪽이 registry가 채워져 있을 때는
dropdown이 되고 그렇지 않으면 free text로 fallback합니다 — 위에서 설계한 그대로이며,
`MappingService`/`DeliveryService`/Subscription API contract에는 변경이 없습니다.
`DemoDataSeeder`도 신선한 환경이 빈 registry가 되지 않도록 demo 시나리오를 위한 예시 field를
seed합니다.
