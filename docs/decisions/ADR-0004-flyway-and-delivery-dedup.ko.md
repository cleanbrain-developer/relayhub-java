> 이 문서는 [`ADR-0004-flyway-and-delivery-dedup.md`](ADR-0004-flyway-and-delivery-dedup.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# ADR-0004: Flyway Migrations and Delivery-Level Idempotency

- Status: Accepted
- Date: 2026-09-10
- Deciders: Project maintainer

## Context

Spec 003이 반영된 뒤 `docs/status/current-state.md`에서 두 가지 gap이 flag되었습니다:

1. `ddl-auto: update`는 이미 row가 존재하는 Postgres volume에 대해 column을 nullable에서 `NOT NULL`로 진화시킬 수 없습니다(Spec 002에서 `DeliveryAttempt.deliveryId`를 추가할 때 발생했음); 그때의 workaround는 local Docker volume을 재생성하는 것이었지만, 실제 데이터가 존재하게 되면 더 이상 받아들일 수 없습니다.
2. Spec 003의 "Deliberately out of scope" gap: Kafka에 의해 재전달된 delivery task(예: `DeliveryWorker`가 consumer offset을 commit하기 전에 crash한 뒤)는 `DeliveryService.deliver(...)`를 다시 호출하여 동일한 (Event, Subscription) pair에 대해 두 번째 `Delivery` row를 만들고, 전체 retry loop를 다시 실행하며 Target을 다시 호출하게 됩니다.

maintainer는 migration tool로 Liquibase 대신 Flyway를 선택했습니다 — Spring Boot ecosystem에서 더 널리 사용되는 옵션이며, Spring Boot 자체의 reference documentation에서도 가장 먼저 언급됩니다; 그 plain-SQL migration은 이 project의 기존 plain-SQL-first tooling 선택과도 잘 맞습니다(배워야 할 XML/YAML changeset format이 없음).

## Decision

### Schema migrations: Flyway

- `db/migration/V1__init_schema.sql`은 실제 Hibernate `ddl-auto: update`가 생성한 Postgres schema(`pg_dump --schema-only`로 dump됨)를 손으로 정리한 baseline입니다 — 의미 있는 constraint 이름이 Hibernate의 자동 생성된 hash를 대체하지만, 모든 column type, nullability, check constraint는 정확히 일치합니다.
- `test` profile 밖에서는 `spring.jpa.hibernate.ddl-auto`가 `validate`입니다: Hibernate는 이제 entity가 Flyway가 소유한 schema와 일치하는지만 확인하며, 그곳에서 DDL을 생성하거나 변경하지 않습니다.
- `test` profile(H2)은 `ddl-auto: create-drop`을 유지하고 Flyway를 비활성화합니다(`spring.flyway.enabled: false`) — migration SQL은 Postgres-specific하며 빠른 H2 suite에서는 설계상 실행되지 않습니다. `PostgresKafkaIntegrationTest`(Testcontainers, real Postgres)가 실제로 `V1__init_schema.sql`을 처음부터 끝까지 실행하며, 이는 매 `./gradlew test`마다 수행됩니다.

### Delivery-level idempotency

- `deliveries (event_id, subscription_id)`에 unique constraint를 추가했습니다.
- `DeliveryService.deliver(...)`는 이제 먼저 `DeliveryRepository.findByEventIdAndSubscriptionId(...)`를 확인합니다; 해당 pair에 대해 이미 `Delivery`가 존재하면 재시도 없이 그 row를 반환합니다 — 이것이 일반적인 경우(worker 재시작 후의 순차적 재전달)이며 SELECT 이상은 필요하지 않습니다.
- 진짜 동시성 경우(동일한 pair를 두고 겹치는 두 transaction이 경쟁하는 경우, 하나의 Kafka partition은 보통 한 번에 하나의 consumer가 처리하므로 consumer-group rebalance의 짧은 순간에만 현실적으로 가능함)는 in-process로 잡아서 복구하지 않도록 의도적으로 남겨두었습니다. 첫 번째 implementation 시도는 `DataIntegrityViolationException`을 잡아서 같은 transaction 안에서 다시 query했지만, Postgres는 하나의 statement가 constraint를 위반하면 그 transaction을 이후 statement에 대해 사용 불가능하게 만들기 때문에 그 fallback query 자체가 실패하게 됩니다. 대신 constraint violation은 `@KafkaListener`가 호출하는 method 밖으로 그대로 전파되도록 두며, Spring Kafka의 일반적인 재전달이 message를 재시도하고, 그 재시도의 SELECT가 다른 transaction이 commit한 row를 찾게 됩니다.

## Consequences

### Positive

- 앞으로의 실제 schema 변경은 Hibernate가 DDL을 추론하는 대신 명시적이고, versioning되고, review 가능한 SQL file이 됩니다 — Spec 002의 NOT-NULL migration 실패를 일으켰던 바로 그 gap이 닫힙니다.
- `PostgresKafkaIntegrationTest`는 이제 test 실행에서 Hibernate가 우연히 생성하는 schema가 아니라 실제 migration path를 자동으로 실행합니다.
- Delivery-task 재전달(Spec 003의 known gap)이 새로운 infrastructure 없이 일반적인 경우에 대해 처리되며, 드문 race case는 안전하게 실패합니다(재시도되며, 조용히 중복되거나 유실되지 않음).

### Costs and risks

- 앞으로의 모든 schema 변경은 새로운 `V<n>__description.sql` file과, 그것이 `validate`와 호환되는지에 대한 수동 review가 필요합니다 — 이는 제거된 `ddl-auto: update` risk와 맞바꾼 의도적인 비용(explicitness)입니다.
- H2 test profile은 더 이상 migration 자체가 깨끗하게 적용되는지 증명하지 않습니다; `PostgresKafkaIntegrationTest`만이 그것을 증명하며, Docker를 필요로 합니다.
- delivery-task dedup의 concurrent-race path는 automated test로 커버되지 않습니다(두 transaction을 의도적으로 경쟁시켜야 하며, rebalance-window에만 해당하는 edge case에 비해 과도한 노력임) — 더 복잡한 code로 해결하는 대신 known, documented gap으로 받아들여졌습니다.

## Alternatives considered

### Liquibase instead of Flyway

Liquibase의 changeset model(XML/YAML/JSON, built-in diffing과 rollback authoring 포함)은 더 feature-rich하지만, Flyway의 plain-SQL-first 접근이 이미 raw SQL 작성에 익숙한 project에 더 단순하다고 판단되었고, 현재 Spring Boot project에서 더 흔한 default입니다.

### Catch-and-recover `DataIntegrityViolationException` for delivery dedup

Postgres의 aborted-transaction 동작으로 인해 naive한 catch-and-re-query 패턴이 하나의 transaction 안에서 안전하지 않다는 것을 발견한 뒤 rejected되었습니다(위의 "Decision" 참고). `REQUIRES_NEW` nested-transaction variant도 고려되었지만, self-invocation/proxy 복잡성(두 번째 collaborator bean이 필요함)을 추가하게 되며, race window가 충분히 좁아서 "fail and let Kafka retry"가 합리적이고 훨씬 단순한 답입니다.
