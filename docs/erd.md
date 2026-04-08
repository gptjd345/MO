# ERD

## 다이어그램

```mermaid
erDiagram
    users {
        BIGINT id PK
        VARCHAR email UK
        VARCHAR password
        VARCHAR nickname
        VARCHAR plan "FREE | PRO"
        INTEGER score
        INTEGER token_version
        INTEGER ranking_version
    }

    todos {
        BIGSERIAL id PK
        VARCHAR title
        TEXT content
        BOOLEAN is_completed
        DATE start_date
        DATE end_date
        DATE completed_at
        VARCHAR priority "HIGH | MEDIUM | LOW"
        BIGINT user_id FK
    }

    weekly_stats {
        BIGSERIAL id PK
        BIGINT user_id FK
        INTEGER year
        INTEGER week_number
        INTEGER completed_count
        INTEGER scheduled_count
        FLOAT completion_rate
        BOOLEAN goal_achieved
    }

    streak_stats {
        BIGSERIAL id PK
        BIGINT user_id FK
        INTEGER current_streak
        INTEGER longest_streak
        INTEGER last_week_achieved
        INTEGER last_year_achieved
        BOOLEAN is_freezed
    }

    todo_events {
        BIGSERIAL id PK
        BIGINT user_id FK
        BIGINT todo_id
        VARCHAR event_type "COMPLETED | CANCELLED | UNCOMPLETED"
        DATE event_date
        TIMESTAMP created_at
    }

    weekly_goals {
        BIGSERIAL id PK
        BIGINT user_id FK
        INTEGER year
        INTEGER week_number
        INTEGER goal_count
    }

    payments {
        BIGSERIAL id PK
        BIGINT user_id FK
        BIGINT amount
        VARCHAR status "INIT | PENDING | SUCCESS | FAIL"
        VARCHAR idempotency_key
        VARCHAR pg_transaction_id
        VARCHAR fail_reason
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    refresh_tokens {
        BIGSERIAL id PK
        BIGINT user_id FK
        VARCHAR jti UK
        TIMESTAMP expires_at
        TIMESTAMP created_at
        TIMESTAMP revoked_at
    }

    users ||--o{ todos : "작성"
    users ||--o{ payments : "결제"
    users ||--o{ refresh_tokens : "발급"
    users ||--o{ weekly_stats : "집계"
    users ||--o| streak_stats : "스트릭"
    users ||--o{ todo_events : "이벤트"
    users ||--o{ weekly_goals : "목표"
```

---

## 설계 의도

### users

| 컬럼 | 설계 의도 |
|------|-----------|
| `plan` | `FREE` / `PRO` 두 가지 플랜을 문자열로 관리합니다. 현재 플랜 종류가 단순하여 별도 테이블로 분리하지 않았습니다. |
| `score` | 랭킹 조회 시 Redis ZSET을 사용하지만, Redis 장애 시 DB가 source of truth 역할을 합니다. 매일 새벽 RankingRebuildJob이 이 값을 기준으로 Redis를 전체 재구성합니다. |
| `token_version` | 비밀번호 변경 등 보안 이벤트 발생 시 증가시켜 모든 디바이스의 Access Token을 일괄 무효화합니다. |
| `ranking_version` | Redis Stream 이벤트의 중복 처리를 방지하기 위한 버전입니다. `token_version`과 목적이 달라 의도적으로 분리했습니다. — 인증 이벤트가 랭킹 deduplication에 영향을 주지 않도록 하기 위함입니다. |

### todos

| 컬럼 | 설계 의도 |
|------|-----------|
| `completed_at` | 완료 처리 시각을 기록합니다. `NULL`이면 미완료 상태입니다. 완료 취소 시 `NULL`로 초기화하며, 점수 차감 계산의 기준값으로 사용됩니다. |
| `start_date` / `end_date` | `end_date` 기준으로 기한 내 완료(+10점) / 기한 초과 완료(+5점)를 구분합니다. |

### stats 테이블 군

stats 관련 테이블은 **배치 집계 결과를 저장하는 read-optimized 구조**입니다. 실시간 집계 대신 배치로 사전 계산하여 조회 성능을 보장합니다.

| 테이블 | 설계 의도 |
|--------|-----------|
| `todo_events` | 완료(`COMPLETED`)/취소(`CANCELLED`)/실행취소(`UNCOMPLETED`) 이벤트를 원본 그대로 보존합니다. 배치가 이 테이블을 소스로 삼아 stats를 재계산합니다. |
| `weekly_stats` | 주별 완료 수와 주간 목표 달성 여부(`goal_achieved`)를 저장합니다. Redis Streams `weekly-group` 컨슈머가 실시간으로 갱신하고, 배치가 매일 새벽 재계산하여 최종 일관성을 보장합니다. |
| `streak_stats` | 주간 목표 연속 달성 횟수를 관리합니다. 1회 미달성 시 즉시 리셋하지 않고 `is_freezed`로 유예를 주는 freeze 정책을 적용합니다. |
| `weekly_goals` | `(user_id, year, week_number)` 복합 유니크로 주차별 목표를 관리합니다. 당일 월요일 또는 전주 일요일에만 설정 가능하도록 서버에서 제한합니다. |

### payments

| 컬럼 | 설계 의도 |
|------|-----------|
| `idempotency_key` | 클라이언트가 UUID로 생성하여 전송합니다. `(user_id, idempotency_key)` 복합 유니크 인덱스로 중복 결제를 차단합니다. |
| `status` | `INIT → PENDING → SUCCESS / FAIL` 순서로 전이됩니다. `PENDING` 상태를 두는 이유는 처리 중 서버가 재시작될 경우 미완 결제를 식별하고 복구하기 위함입니다. |
| `pg_transaction_id` | PG사가 반환하는 외부 거래 ID입니다. 결제 성공 시에만 기록되며, PG사와의 대사(reconciliation)에 사용됩니다. |

### refresh_tokens

| 컬럼 | 설계 의도 |
|------|-----------|
| `jti` | JWT ID로 UUID를 사용합니다. Refresh Token 자체를 저장하는 대신 JTI만 저장하여 크기를 최소화했습니다. |
| `revoked_at` | `NULL`이면 유효한 토큰입니다. 로그아웃 시 이 값을 기록하여 재사용을 차단합니다. 토큰 재발급(Rotation) 시에도 기존 JTI를 revoke하고 새 토큰을 발급합니다. |
| `ON DELETE CASCADE` | 사용자 탈퇴 시 연관된 Refresh Token을 자동으로 삭제합니다. |
