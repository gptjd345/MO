# 랭킹 시스템 설계

## 개요

일정 완료 시 점수가 실시간으로 반영되는 랭킹 시스템입니다.
단순히 DB 쿼리로 랭킹을 집계하는 대신 Redis를 중심에 둔 비동기 파이프라인으로 설계했습니다.

---

## 왜 DB 집계 방식을 쓰지 않았는가

`SELECT * FROM users ORDER BY score DESC` 로 랭킹을 구현하면 사용자가 늘어날수록 쿼리 비용이 선형적으로 증가합니다.
Redis Sorted Set(ZSET)은 삽입/조회 모두 **O(log n)** 이며, 랭킹 조회에 최적화된 자료구조입니다.

---

## 아키텍처: 3단계 파이프라인

```
[1단계] 일정 완료 (TodoService)
    → DB 트랜잭션 커밋 (score, ranking_version 업데이트)
    → Redis Stream 이벤트 발행 (트랜잭션 바깥, try-catch)

[2단계] 이벤트 소비 (RankingWorker → RankingProcessor)
    → 100ms 간격 폴링, 배치 10건 처리
    → 버전 검증 후 ZSET 업데이트

[3단계] 일별 재구성 (RankingRebuildJob)
    → 매일 새벽 1시 10분, DB 전체 데이터로 ZSET 재구성
```

---

## 기술적 고민 1 — DB 정합성 vs Redis 속도

### 문제

일정 완료 → DB 커밋 → Redis 업데이트 사이에 Redis 장애가 발생하면 랭킹이 틀어집니다.
반대로 Redis 업데이트 후 DB 커밋이 실패하면 유령 점수가 생깁니다.

### 해결: DB 커밋 후 이벤트 발행

Redis 발행을 **DB 트랜잭션 바깥**에 두고 try-catch로 감쌌습니다.

```
@Transactional
public void completeTodo() {
    // 1. DB 업데이트 (score, ranking_version)
}
// 2. 트랜잭션 종료 후 Redis Stream 발행 (실패해도 DB는 안전)
try { redisStream.publish(event); } catch (Exception e) { log.warn(...); }
```

Redis 발행이 실패해도 DB의 score는 정확합니다. 매일 새벽 **RankingRebuildJob**이 DB 기준으로 전체 재구성하므로 최대 하루 이내에 자동 복구됩니다.

---

## 기술적 고민 2 — 중복 이벤트 처리 (deduplication)

### 문제

네트워크 지연이나 재시도로 동일한 완료 이벤트가 Stream에 두 번 발행될 수 있습니다.
점수가 중복으로 반영되면 랭킹이 오염됩니다.

### 해결: ranking_version 기반 deduplication

`users` 테이블에 `ranking_version` 컬럼을 두고, 점수 변경 시마다 1씩 증가시킵니다.
이벤트에 발행 시점의 version을 함께 담아 전송합니다.

```
RankingProcessor.process(event):
  currentVersion = Redis Hash에서 조회 (ranking:versions → userId)
  if event.version <= currentVersion:
      skip (이미 처리된 이벤트)
  else:
      ZSET score 업데이트
      Hash version 업데이트
```

`ranking_version`은 인증 무효화에 쓰는 `token_version`과 **의도적으로 분리**했습니다.
두 버전이 같은 컬럼을 공유하면 인증 이벤트가 랭킹 deduplication을 오염시킬 수 있기 때문입니다.

---

## 기술적 고민 3 — 배치 완료 시 이벤트 중복 발행 방지

여러 일정을 한 번에 완료하는 batch-complete API는 **단일 트랜잭션**으로 처리합니다.
개별 완료 이벤트를 N번 발행하는 대신, 합산 점수를 담은 **이벤트 1건**만 발행합니다.

```
PATCH /api/todos/batch-complete
  → 단일 @Transactional
  → ranking_version 1회만 증가
  → Stream 이벤트 1건 발행 (totalPointsEarned)
```

---

## 기술적 고민 4 — 랭킹 데이터 일별 재구성 (Rebuild & Swap)

### 문제

실시간 이벤트 기반 업데이트만으로는 누락/오차가 누적될 수 있습니다.
주기적으로 DB 기준으로 전체 재구성이 필요한데, 재구성 중에도 랭킹 조회가 중단 없이 가능해야 합니다.

### 해결: Rebuild & Swap 패턴

신규 키(`ranking:new`)에 데이터를 먼저 채운 뒤, Redis의 원자적 `RENAME` 명령으로 교체합니다.

```
1. ranking:new에 DB 전체 사용자 데이터 채움 (시간 소요)
2. 원자적 스왑:
   RENAME ranking     → ranking:old
   RENAME ranking:new → ranking        ← 클라이언트는 이 순간부터 새 데이터 조회
3. ranking:old에 TTL 1시간 설정 후 자동 삭제
```

`RENAME`은 Redis 단일 명령이므로 클라이언트는 빌드 중인 불완전한 데이터를 읽을 수 없습니다.
분산 락 없이도 무중단 교체가 가능합니다.

---

## Redis 자료구조 선택 이유

| 자료구조 | 키 | 용도 |
|----------|-----|------|
| Sorted Set (ZSET) | `ranking` | 점수 기반 순위 조회, O(log n) |
| Hash | `ranking:versions` | userId별 ranking_version 추적 |
| Stream | `ranking:events` | 비동기 이벤트 전달, Consumer Group으로 수신 보장 |

ZSET의 `ZREVRANGEBYSCORE`로 "내 위 3명 + 나 + 아래 2명"을 단일 쿼리로 효율적으로 조회합니다.
