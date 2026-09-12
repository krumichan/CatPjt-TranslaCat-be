# Speaking 평가 작업과 장애 복구

## 적용 기준

세션 전체 평가와 Read Aloud 문제별 평가를 `language_learning_speaking_evaluation_job`에 기록한다.
문제 번호 0은 세션 전체 평가, 1~5는 제출한 Read Aloud 문제다. 같은 세션/문제에는 작업을 하나만 만든다.
문제풀, 학습 설정, Sudachi 파일은 이 기능에서 변경하지 않는다. 기존 데이터를 삭제하는 SQL도 포함하지 않는다.

## 상태와 트랜잭션

- 제출/재시도: 소유권 확인 및 Session 잠금 → 원본 요청 snapshot/작업 PENDING 저장 → AFTER_COMMIT 이벤트.
- 작업 선점: Session → Job 순서로 잠금 → RUNNING/임대 만료 시각/새 claim token 저장.
- AI 요청: DB 트랜잭션을 열지 않고 호출한다.
- 결과: Session → Job 잠금과 token 확인 → 검증/결과/Metric/History/Profile/사용량/작업 성공을 한 트랜잭션으로 반영한다.
- 예외: 실패한 결과 트랜잭션이 롤백된 뒤 별도 REQUIRES_NEW 트랜잭션에서 FAILED를 기록한다.

성공 결과는 세션 기준으로 재사용한다. 요청 policy version과 AI가 돌려준 evaluation version의 문자열 차이에 의존하지 않는다.
동일 작업의 중복 콜백은 무시한다. 서로 다른 세션의 같은 사용자 Profile 갱신도 사용자 잠금과 evidence의 잠금 조회로 직렬화한다.

## 재시작과 중복

이벤트는 빠른 실행 신호일 뿐이며, 작업의 원본은 DB다. 10초마다 PENDING과 임대가 만료된 RUNNING을 조회한다.
실행 중 프로세스가 사라진 작업은 임대 만료 후 다시 선점한다. 이전 token의 성공/실패 응답은 새 결과를 덮지 못한다.

이 방식은 DB 결과 반영의 중복을 막는다. 외부 AI 요청 자체의 엄격한 exactly-once를 보장하지는 않는다.
프로세스 종료/응답 유실 후 재실행되면 외부 Provider 호출과 비용이 중복될 수 있다.

기본 설정:

```properties
language-learning.speaking.evaluation-job.workers=4
language-learning.speaking.evaluation-job.lease-seconds=900
language-learning.speaking.evaluation-job.max-recoveries=2
language-learning.speaking.evaluation-job.recovery-delay-ms=10000
```

900초 임대는 재시작 직후 즉시 회수하는 설정이 아니다. 최악에는 임대 만료까지 최대 약 15분과 조회 간격을 기다린다.
AI/HTTP의 총 timeout 및 내부 retry보다 충분히 긴 임대를 사용해야 한다. 긴 정상 작업이 임대를 초과하면 재실행될 수 있다.
자동 회수 2회를 소진하면 FAILED가 된다. 사용자의 수동 재시도 한도는 생성 시 저장한 세션 정책을 따른다.
작업자 pool이 가득 찬 경우는 5초 뒤 PENDING으로 돌리고 회수/수동 재시도 횟수를 차감하지 않는다.

## API/프런트엔드 계약

기존 세션 평가 재시도 API는 유지한다. Read Aloud에는 다음 API를 추가한다.

```text
POST /api/v1/language-learning/speaking/sessions/{sessionId}/read-aloud/problems/{problemIndex}/evaluation/retry
```

소유자의 제출된 FAILED 문제만 재시도할 수 있다. 종료된 세션에서도 가능하지만 녹음/발화 제외를 다시 열어 주지 않는다.
PENDING/EVALUATING 상태의 중복 요청은 새 작업이나 새 횟수를 만들지 않는다. 성공/증거 부족/평가 생략 결과는 재평가하지 않는다.
수동 재시도에서도 녹음 revision과 발화/문맥 snapshot은 그대로 사용하며 requestId만 새 시도에 맞춘다.
문제 응답에 `manualRetryCount`, `manualRetryLimit`을 추가한다.

AI의 사전 `INSUFFICIENT_EVIDENCE`는 무점수/빈 지표/사전 실패 사유가 있는 정상 종료다.
`EVALUATED` 응답의 8개 지표, 점수 범위, evidence 소속, Request/Session 일치는 계속 검증한다.
평가 비활성화 세션의 개별 문제는 NOT_REQUESTED로 저장하고 AI 작업을 만들지 않는다.
5문제/각각 2~3발화/재녹음 덮어쓰기 기준은 변경하지 않는다.

## 스키마와 데이터 정리

새 작업 테이블과 문제 평가 테이블의 `manual_retry_count`, `manual_retry_limit` 컬럼이 필요하다.
기존 `ddl-auto=update`를 사용한다면 쓰기 권한/실제 생성 결과를 점검한다. 스키마 관리가 별도인 환경은 명시적인 additive migration이 필요하다.
평가 작업 snapshot에는 제출한 학습 내용이 포함되므로 세션 이력과 같은 접근/보관/삭제 정책의 대상이다.
학습 데이터를 정리할 때 새 job의 session FK도 고려한다. 문제풀/기본 설정 삭제나 FK 검사 해제는 이 패치가 수행하지 않는다.
이전 구현에서 이미 정체된 평가를 자동으로 job에 변환하는 backfill은 하지 않는다. 기존 학습 이력 정리를 전제로 한 릴리스다.
단순 DELETE는 오래된 unique index를 제거하지 않으므로 과거 스키마가 남는 문제는 별도로 점검해야 한다.

## 검증

```powershell
.\gradlew.bat test --tests "*SpeakingEvaluationJob*" --tests "*SpeakingReleaseContractTest" --tests "*SpeakingReadAloudReleasePolicyTest"
.\gradlew.bat clean test
```

`SpeakingEvaluationJobIntegrationTest`는 H2 + 실제 Spring 트랜잭션/EventListener/Repository로 실패 저장,
결과 반영 롤백, 커밋 후 재시도, 동시 선점, stale token, 소유권, 종료 후 문제 재시도, Profile 누적을 검사한다.
AI 호출은 mock이다. MySQL의 실제 잠금 동작, 실제 HTTP/Provider 및 프로세스 강제 종료는 staging에서 추가 확인한다.
두 저장소의 `speaking-release/*.json`은 AI의 실제 평가 service + 결정적 Provider 대역으로 만든 동일 fixture다.

```powershell
# AI 저장소 루트에서 실행. 새 Provider 호출은 하지 않는다.
python -m scripts.export_speaking_release_contract ..\CatPjt-TranslaCat-be\src\test\resources\speaking-release
```
