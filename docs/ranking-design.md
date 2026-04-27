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
[1단계] 일정 완료 (TodoService → TodoEventListener)
    → DB 트랜잭션 커밋 (score 업데이트)
    → @TransactionalEventListener(AFTER_COMMIT): Redis Stream("todo-stream") 이벤트 발행

[2단계] 이벤트 소비 (RankingWorker → RankingProcessor)
    → 100ms 간격 폴링, 배치 10건 처리
    → DB에서 최신 score 조회 후 ZSET 업데이트

[3단계] 일별 재구성 (RankingRebuildJob)
    → 매일 새벽 1시 10분, DB 전체 데이터로 ZSET 재구성
```

---

## 기술적 고민 1 — DB 정합성 vs Redis 속도

### 문제

일정 완료 → DB 커밋 → Redis 업데이트 사이에 Redis 장애가 발생하면 랭킹이 틀어집니다.
반대로 Redis 업데이트 후 DB 커밋이 실패하면 유령 점수가 생깁니다.

### 해결: @TransactionalEventListener(AFTER_COMMIT)

`TodoService`는 `ApplicationEventPublisher`로 내부 이벤트를 발행하고, `TodoEventListener`가 DB 커밋 완료 후에만 Redis Stream에 발행합니다.

```
@Transactional
public void completeTodo() {
    // 1. DB 업데이트 (score)
    applicationEventPublisher.publishEvent(new TodoCompletedEvent(...));
}

@TransactionalEventListener(phase = AFTER_COMMIT)
public void onTodoCompleted(TodoCompletedEvent event) {
    // 2. DB 커밋 확정 후 Redis Stream("todo-stream") 발행
    eventPublisher.publish(event);
}
```

DB 롤백 시 AFTER_COMMIT 리스너가 실행되지 않아 유령 이벤트 발행을 원천 차단합니다.
Redis 발행이 실패해도 DB의 score는 정확하며, 매일 새벽 **RankingRebuildJob**이 DB 기준으로 전체 재구성하므로 최대 하루 이내에 자동 복구됩니다.

---

## 기술적 고민 2 — 중복 이벤트 처리 (deduplication)

### 문제

네트워크 지연이나 재시도로 동일한 완료 이벤트가 Stream에 두 번 발행될 수 있습니다.

### 해결: DB 조회 기반 처리로 자연스러운 멱등성 확보

`RankingProcessor`는 이벤트 페이로드에서 score를 읽지 않고, 이벤트 소비 시점에 DB에서 최신 score를 직접 조회합니다.

```
RankingProcessor.process(event):
  score = userRepository.findById(event.userId).getScore()  // 항상 현재 DB값
  ZSET score 업데이트
```

동일 이벤트가 두 번 처리되더라도 두 번 모두 같은 score로 ZSET을 갱신하므로 결과가 동일합니다.
페이로드에 score를 담으면 이벤트 발행 시점과 소비 시점 사이의 score 변화가 반영되지 않는 문제도 함께 해결됩니다.

이 방식이 가능한 이유:
- 랭킹은 "내 위 3명 / 아래 2명"을 보여주는 구조로 정밀한 순위 경쟁이 없어 레이턴시에 덜 민감합니다.
- 할일 완료/취소가 고빈도 동시 이벤트가 아니어서 DB 조회 비용이 문제되지 않습니다.
- 이벤트 페이로드를 컨슈머별 관심사로 오염시키지 않아 유지보수에 유리합니다.

---

## 기술적 고민 3 — 배치 완료 시 이벤트 발행

여러 일정을 한 번에 완료하는 batch-complete API는 **단일 트랜잭션**으로 score를 합산하여 저장합니다.
이벤트는 완료된 일정별로 N건 발행하지만, `RankingProcessor`가 소비 시점에 항상 DB의 최신 score를 읽으므로 모든 이벤트 처리 결과가 최종 score를 반영합니다.

```
PATCH /api/todos/batch-complete
  → 단일 @Transactional (score 합산 저장)
  → Stream 이벤트 N건 발행 (todoId별 1건)
  → RankingProcessor: 매번 DB에서 현재 score 조회 → ZSET 갱신
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
| Stream | `todo-stream` | 비동기 이벤트 전달, ranking-group / stats-group이 fan-out 소비 |

ZSET의 `ZREVRANGEBYSCORE`로 "내 위 3명 + 나 + 아래 2명"을 단일 쿼리로 효율적으로 조회합니다.
