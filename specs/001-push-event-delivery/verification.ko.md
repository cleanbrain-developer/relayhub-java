> 이 문서는 [`verification.md`](verification.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 001: Verification

## Definition of done (`AGENTS.md`와 `docs/product/goals.md` 기준)

- `./gradlew test`가 통과한다.
- `docker compose up`이 정상적으로 시작된다.
- Integration/contract test가 통과한다.
- `spec.md`의 acceptance scenario가 end-to-end로 재현 가능하다(예: `scripts/compose-smoke-test.sh`가 존재하게 되면 이를 통해).
- 관련 design/status 문서가 동일한 변경 안에서 함께 갱신된다(최소한 `docs/status/current-state.md`).

코드가 컴파일된다는 사실만으로는 완료의 충분한 증거가 되지 않는다 — `AGENTS.md` 참고.
