# MO(Momentum)

할 일 관리를 넘어 완료 이력과 주간 목표를 추적하며 꾸준함을 만들어가는 생산성 웹 애플리케이션입니다.
무료/Pro 플랜 구분과 PG 연동을 가정한 결제 플로우(멱등성 보장, 지수 백오프 재시도, 상태 머신 설계)를 포함한 풀스택 프로젝트로, 실무에서 마주치는 설계 문제들을 직접 고민하고 구현하는 데 초점을 뒀습니다.

## 기술 스택

| 레이어 | 기술 |
|--------|------|
| Backend | Spring Boot 3.2, Java 21, Spring Security, JPA (Hibernate) |
| Database | PostgreSQL 16, Liquibase |
| Cache | Redis 7 (Streams, ZSET, Hash) |
| Auth | JWT (Access Token + Refresh Token, Token Versioning) |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, TanStack Query |
| Infra | AWS CloudFront + S3 + EC2, Docker Compose, GitHub Actions |

## 아키텍처

```
Client (HTTPS)
    └─► CloudFront
            ├── /api/*  ─► EC2:8080 (Spring Boot)
            └── /*      ─► S3 (React 정적 빌드)

EC2 (Docker Compose)
    ├── spring         :8080
    ├── postgres       :5432
    ├── redis          :6379
    ├── prometheus     :9090
    ├── grafana        :3000
    ├── pushgateway    :9091
    └── node-exporter  :9100
```

CloudFront를 단일 진입점으로 사용해 프론트엔드(S3)와 API(EC2)를 동일 도메인에서 서빙합니다. 클라이언트는 API를 상대 경로(`/api/*`)로 호출하므로 CORS 이슈가 없습니다.

## 프로젝트 구조

```
.
├── backend/
│   └── src/main/java/com/todo/
│       ├── controller/        # AuthController, TodoController, PaymentController, RankingController
│       ├── service/           # AuthService, JwtService, TodoService, PaymentService
│       ├── entity/            # User, Todo, Payment, RefreshToken
│       ├── security/          # JwtAuthFilter
│       ├── exception/         # CustomException, GlobalExceptionHandler, ErrorCode
│       ├── event/             # TodoCompletedEvent, TodoCanceledEvent, EventPublisher, TodoEventListener
│       ├── messaging/         # StreamNames, ConsumerGroups, RedisStreamConfig, ConsumerGroupInitializer
│       ├── payment/
│       │   ├── domain/        # PgClient (interface), PgRequest, PgResult
│       │   └── infrastructure/ # FakePgClient
│       ├── ranking/
│       │   ├── domain/        # Ranking, RankingService, RankingScoreRow
│       │   ├── infrastructure/ # RedisRankingRepository
│       │   └── worker/        # RankingWorker, RankingProcessor, RankingRebuildJob
│       ├── stats/
│       │   ├── batch/         # DailyStatsBatchJob (Spring Batch, 2 Steps), BatchMetricsPusher
│       │   ├── controller/    # StatsController
│       │   ├── domain/        # WeeklyStat, StreakStat, TodoEvent, WeeklyGoal
│       │   ├── dto/           # StreakResponse, CalendarDayResponse 등
│       │   ├── infrastructure/ # 각 도메인 Repository
│       │   └── service/       # StatsService, WeeklyGoalService
│       ├── config/            # SecurityConfig
│       └── repository/
├── frontend/src/
│   ├── pages/                 # auth-page, dashboard-page, calendar-page
│   ├── components/            # layout-shell, UI 컴포넌트
│   └── hooks/                 # use-auth, use-todos, use-ranking
├── monitoring/
│   ├── prometheus.yml         # prod scrape 설정
│   ├── prometheus.dev.yml     # dev scrape 설정 (host.docker.internal)
│   ├── grafana-dashboard.json # 애플리케이션 모니터링 대시보드
│   └── provisioning/          # Grafana 시작 시 datasource·dashboard 자동 등록
├── docs/                      # 설계 문서
├── .github/workflows/ci.yml   # GitHub Actions CI/CD
└── docker-compose.prod.yml
```

## 설계 문서

설계 의도와 기술적 고민/해결 과정을 별도 문서로 정리했습니다.

| 문서 | 내용 |
|------|------|
| [erd.md](docs/erd.md) | ERD 및 테이블 설계 의도 |
| [auth-design.md](docs/auth-design.md) | JWT Token Versioning + Refresh Token Rotation 설계 |
| [ranking-design.md](docs/ranking-design.md) | Redis Streams 기반 실시간 랭킹 + Rebuild & Swap 설계 |
| [payment-design.md](docs/payment-design.md) | 결제 멱등성 + 지수 백오프 재시도 설계 |
| [stats-design.md](docs/stats-design.md) | Spring Batch 기반 통계 집계 + 스트릭 + Journey 달력 설계 |

## 주요 기술적 구현

### JWT Token Versioning + Refresh Token Rotation

두 가지 무효화 전략을 목적에 따라 분리해 적용했습니다.

**Token Versioning — 전체 디바이스 일괄 로그아웃**

비밀번호 변경 등 보안 이벤트 발생 시 `token_version`을 증가시켜 해당 사용자의 모든 디바이스 세션을 한 번에 무효화합니다.

- `users` 테이블의 `token_version` 컬럼을 증가
- Access Token 페이로드에 `tv`(token version) claim 포함
- 요청마다 `JwtAuthFilter`에서 DB 버전과 비교 — 불일치 시 401 반환
- Redis에 token version을 캐싱하여 매 요청마다 DB 조회를 방지 (Redis miss 시 DB fallback)

```
비밀번호 변경 → token_version++ → Redis 캐시 evict
→ 모든 디바이스의 기존 토큰: tv != 현재 버전 → 401
```

**Refresh Token Rotation — 단일 디바이스 로그아웃**

로그아웃은 해당 디바이스의 Refresh Token만 무효화합니다.

- Refresh Token은 JTI(JWT ID)를 UUID로 생성하여 PostgreSQL에 저장
- 로그아웃 시 해당 JTI의 `revoked_at` 기록 → 이후 재발급 요청 차단
- 재발급 시 기존 JTI 무효화 + 새 토큰 발급 (Rotation)
- Access Token 만료 15분 / Refresh Token 만료 7일

### Redis Streams 기반 실시간 랭킹

일정 완료 시 점수가 반영되는 랭킹 시스템을 Redis Streams 기반 비동기 파이프라인으로 구현했습니다.

**점수 이벤트 발행 (TodoService → TodoEventListener)**

```
일정 완료 → @Transactional 내에서 score 업데이트 + ApplicationEventPublisher.publishEvent()
→ DB 커밋 완료
→ @TransactionalEventListener(AFTER_COMMIT): TodoEventListener가 Redis Stream("todo-stream")에 발행
```

`@TransactionalEventListener(AFTER_COMMIT)`을 사용해 DB 커밋이 확정된 이후에만 Redis 이벤트를 발행합니다. Redis 장애 시에도 DB 정합성을 보장하며, DB 롤백 시 Redis 이벤트가 발행되지 않습니다.

**중복 처리 방지 (버전 기반 deduplication)**

동시에 여러 일정을 완료할 경우 배치 API(`PATCH /api/todos/batch-complete`)로 단일 트랜잭션에서 처리하여 이벤트 중복 발행을 원천 차단합니다. 개별 API 경로에서는 `ranking_version`을 이용한 deduplication으로 방어합니다.

- Consumer Group(`ranking-group`)으로 메시지 수신 보장
- `RankingProcessor`에서 Redis Hash에 저장된 현재 버전과 비교 — 이미 처리된 버전이면 스킵
- `@Scheduled(fixedDelay=100)` 폴링으로 최대 100ms 지연

**랭킹 조회**

Redis ZSET으로 본인 기준 상위 3명 / 본인 / 하위 2명을 반환합니다.

```
GET /api/rankings/around
→ ZREVRANGEBYSCORE로 상위 3명, 하위 2명 조회
→ 닉네임 일괄 조회 후 응답 조합
```

**일별 랭킹 재구성 배치 (Rebuild & Swap)**

매일 새벽 1시 10분에 DB 기준으로 Redis 랭킹 데이터를 전체 재구성합니다. 무중단 교체를 위해 Rebuild & Swap 패턴을 적용했습니다.

```
1. ranking:new / ranking:versions:new 에 DB 전체 데이터 채움
2. RENAME ranking → ranking:old
   RENAME ranking:new → ranking
   RENAME ranking:versions → ranking:versions:old
   RENAME ranking:versions:new → ranking:versions
3. ranking:old, ranking:versions:old 에 TTL 1시간 설정 후 자동 삭제
```

`RENAME`은 Redis의 원자적 명령이므로 교체 순간에도 클라이언트는 항상 유효한 데이터를 읽습니다.

### 결제 처리 (멱등성 + 재시도)

- 클라이언트가 `idempotencyKey`를 생성해 전송, 서버는 동일 키 재요청 시 중복 처리를 방지
- PG 호출 실패(Timeout, SystemError) 시 지수 백오프(Exponential Backoff)로 최대 3회 재시도
- `PgClient` 인터페이스로 PG 구현체를 추상화 → `FakePgClient`로 실패/성공 시나리오 시뮬레이션

```java
// 지수 백오프: 200ms → 400ms → 800ms
long delay = (long) Math.pow(2, attempt) * 100;
```

### Spring Batch 기반 통계 집계

일정 완료/취소 이벤트를 `todo_events`에 원본 그대로 보존하고, 매일 새벽 배치가 `daily_stats` / `weekly_stats` / `streak_stats`를 집계합니다.

```
statsJob (매일 새벽 1시)
  ├── Step 1: weeklyStatsStep  — 주별 달성률 + 목표 달성 여부 집계
  └── Step 2: streakStatsStep  — 월요일에만 실행, 스트릭 업데이트
```

이벤트 원본을 보존하는 이유는 배치 오류나 스키마 변경 시 언제든 전체 재계산이 가능하기 때문입니다.

**배치 모니터링 (Pushgateway)**

배치 실행 시간이 짧아 Prometheus의 pull 방식으로는 메트릭을 수집할 수 없습니다. 배치 완료 후 `BatchMetricsPusher`가 Pushgateway에 메트릭을 push하고, Prometheus가 이를 scrape합니다.

```
statsJob 완료 → BatchMetricsPusher.push()
  → batch_job_duration_seconds
  → batch_job_success
  → batch_job_last_run_timestamp_ms
```

**스트릭 freeze 정책**

1회 주간 목표 미달성 시 즉시 리셋하지 않고 `is_freezed` 상태로 1주 유예합니다. 2주 연속 미달성 시에만 streak을 0으로 리셋합니다.

**이번 주 streak 실시간 반영**

배치는 지난 주까지만 처리하므로, 이번 주 완료 이벤트 존재 여부를 DB에서 실시간 조회하여 응답 시 `currentStreak + 1`로 보정합니다.

### DB 마이그레이션 (Liquibase)

- SQL 기반 changeset으로 스키마 이력 관리
- precondition으로 각 changeset의 실행 조건을 명시해 멱등성 보장
- 운영 환경에서만 자동 실행 (`spring.liquibase.enabled=true` in prod profile)

### Docker Compose 운영 구성

- `depends_on: condition: service_healthy` — postgres/redis 헬스체크 통과 후 Spring 기동
- 민감한 정보(`POSTGRES_PASSWORD`, `JWT_SECRET`)는 Docker secrets로 관리 (`/run/secrets/`)
- `.env.prod`로 비민감 환경변수 분리

## CI/CD

```
main 브랜치 push
    ├── frontend-deploy
    │   ├── Vite 빌드
    │   ├── S3 sync (--delete)
    │   └── CloudFront Invalidation
    └── backend-deploy
        ├── Maven 빌드 (mvn package -DskipTests)
        ├── Docker 이미지 빌드 & Docker Hub 푸시 (태그: git sha)
        └── EC2 SSH 접속
            ├── Docker Hub pull (spring 이미지만)
            └── docker compose up -d
```

## 로컬 개발 환경

```bash
# 백엔드 인프라 (PostgreSQL, Redis)
docker compose -f docker-compose.dev.yml up -d

# 프론트엔드 dev server
npm install
npm run dev   # http://localhost:3000

# Spring Boot는 IntelliJ에서 Active Profile: dev 로 실행
```

| 서비스 | 주소 |
|--------|------|
| Frontend (Vite) | http://localhost:3000 |
| Spring Boot API | http://localhost:8080 |
| PostgreSQL | localhost:5433 |
| Redis | localhost:6380 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3002 |
| Pushgateway | http://localhost:9091 |

## 환경 변수

### Spring Boot

| 변수 | 설명 |
|------|------|
| `REDIS_HOST` | Redis 호스트 |
| `REDIS_PORT` | Redis 포트 |
| `JWT_SECRET` | JWT 서명 키 (256비트 이상) |
| `PUSHGATEWAY_URL` | Pushgateway 주소 (기본값: `pushgateway:9091`) |

### PostgreSQL

| 변수 | 설명 |
|------|------|
| `POSTGRES_DB` | 데이터베이스명 |
| `POSTGRES_USER` | 유저명 |
| `POSTGRES_PASSWORD_FILE` | Docker secret 경로 |

### Grafana

| 변수 | 설명 |
|------|------|
| `GRAFANA_ADMIN_PASSWORD` | Grafana 관리자 비밀번호 |

## 주요 기능

- 회원가입 / 로그인 / 로그아웃 (JWT 인증)
- 할 일 CRUD — 제목, 내용, 우선순위, 시작일/마감일
- 서버 사이드 페이징 + 정렬(최신순·이름순·마감일순·우선순위순) + 검색
- Active / Completed 영역 분리 선택, 일괄 완료(batch-complete) / 일괄 취소(batch-undo)
- 완료된 일정 삭제 차단 — 취소 후 삭제하도록 강제하여 점수·통계 정합성 보장
- 일정 완료 시 점수 부여 (+10점 기한 내 / +5점 기한 초과), 취소 시 차감
- 무료 플랜: 최대 10개 / Pro 플랜: 무제한
- PG 결제 연동 (멱등성 보장, 실패 시뮬레이션 포함)
- Redis Streams 기반 실시간 랭킹 (Keepers)
- Journey 달력 — 날짜별 완료 건수 시각화, 클릭 시 Dashboard 해당 항목 하이라이트 이동
- 주간 목표 설정 및 달성 여부 추적 (설정 시점 서버 검증)
- 주간 스트릭 — 연속 목표 달성 횟수, 1회 미달성 freeze 유예 정책
