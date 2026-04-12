# 통계 시스템 설계 (Journey / Keepers)

## 개요

일정 완료 이력을 기반으로 주간 목표 달성 여부, 주간 스트릭, Journey 달력을 제공하는 통계 시스템입니다.
**Redis Streams 기반 실시간 속도 레이어 + Spring Batch 기반 최종 일관성** 구조를 채택했습니다.

---

## 아키텍처 개요

```
일정 완료 / 취소
    ↓
todo_events INSERT  ←── source of truth (PostgreSQL, 영속)
    ↓
Redis Streams (todo:events)
    ├── ranking-group  → Redis ZSET 실시간 업데이트
    └── weekly-group   → weekly_stats 실시간 업데이트

Spring Batch (매일 새벽 1시)
    ├── weeklyStatsStep  → todo_events 기준 weekly_stats 재계산
    └── streakStatsStep  → weekly_stats 기준 streak 업데이트
```

Redis Streams를 선택한 이유:
- **단일 이벤트 fan-out**: 완료 이벤트 하나를 랭킹과 주간통계 두 컨슈머 그룹이 독립적으로 소비합니다.
- **RabbitMQ 대비**: RabbitMQ fanout exchange로 멀티 컨슈머가 가능하지만, PEL 기반 재처리·메시지 영속성·consumer group 오프셋 추적을 직접 구현해야 합니다. Redis는 이미 랭킹 ZSET 용도로 사용 중이어서 추가 인프라 없이 도입이 가능합니다.
- **Kafka**: 현재 트래픽 수준에서는 과도한 운영 비용입니다. 컨슈머 그룹이 늘고 파티셔닝이 필요한 시점에 도입을 고려합니다.

---

## 이벤트 소싱

완료/취소 이벤트를 `todo_events` 테이블에 원본 그대로 보존합니다.

```
일정 완료  → todo_events INSERT (COMPLETED,   event_date)
일정 취소  → todo_events INSERT (CANCELLED,   event_date)  ← 스트림 이전 레거시 타입
일정 실행취소 → todo_events INSERT (UNCOMPLETED, event_date)
```

이벤트 원본을 보존하는 이유:
- 배치가 실패하거나 로직이 변경되어도 `todo_events`를 소스로 삼아 전체 stats를 언제든 재계산할 수 있습니다.
- `POST /dev/batch/recalculateStats`는 `todo_events` 전체를 순회하며 weekly_stats → streak_stats → ranking 순으로 전부 재구성합니다.
- `todo_events`가 비어있는 경우 `POST /dev/batch/backfillTodoEvents`로 완료된 todos 기반 이벤트를 먼저 채운 뒤 재계산합니다.

---

## Redis Streams — weekly-group 컨슈머

`weekly-group` 컨슈머는 `COMPLETED` / `UNCOMPLETED` 이벤트를 받아 `weekly_stats`를 실시간 갱신합니다.

```
COMPLETED  → completedCount + 1, goalAchieved 재평가
UNCOMPLETED → completedCount - 1 (최솟값 0), goalAchieved 재평가
```

undo 처리도 스트림을 통해 처리하는 이유:
- undo로 completedCount가 goal 미만이 되는 경우 `goalAchieved`가 배치 전까지 true로 유지되면 "최근 4주 목표달성률"에 오염된 데이터가 반영됩니다.
- 완료/취소가 대칭적으로 스트림을 타야 실시간 값이 의미 있습니다.

### ACK 처리 및 PEL 재처리

- DB 저장 성공 시에만 ACK → 실패 시 Pending Entries List(PEL)에 남음
- `StatsWorker.retryPendingWeeklyStats()` 가 60초마다 PEL을 스캔하여 idle 30초 이상인 메시지를 XCLAIM으로 재처리
- 재처리 3회 초과 시 poison pill로 판단, ACK 후 포기 → 배치가 야간에 정합성 보정

### 중복 처리 가능성

ACK 실패 후 PEL 재처리 시 같은 메시지를 두 번 처리할 수 있습니다(at-least-once delivery).
멱등성 키로 완전히 차단할 수 있지만, ACK 자체가 단순 Redis 명령이라 실패 확률이 매우 낮고
배치가 매일 새벽 재계산하므로 복잡성 대비 효용이 낮아 적용하지 않았습니다.

---

## Spring Batch 구성

`DailyStatsBatchJob`은 매일 새벽 1시에 실행되며 2개의 Step이 순서대로 동작합니다.

```
statsJob
  ├── Step 1: weeklyStatsStep  — 어제 기준 weekly_stats 재계산 (todos 직접 쿼리)
  └── Step 2: streakStatsStep  — 월요일에만 실행, 직전 주 streak 업데이트
```

배치가 Redis Streams와 함께 존재하는 이유 (Lambda Architecture):
- **speed layer** (Redis Streams): 실시간 근사치. 완료/취소 직후 weekly_stats 반영.
- **batch layer** (Spring Batch + todo_events): 최종 일관성 보장. Redis 장애 또는 publish 실패로 스트림에 들어가지 못한 이벤트, ACK 실패 후 poison pill 처리된 메시지를 PostgreSQL의 `todo_events`(source of truth)로 매일 교정합니다.

Chunk 방식 대신 **Tasklet** 방식을 선택한 이유:
하루치 이벤트를 한 번에 집계하는 구조라 대용량 페이지 처리가 필요 없습니다. Tasklet이 더 단순하고 명확합니다.

---

## 기술적 고민 1 — 최근 4주 목표달성률 계산

단순히 `weekly_stats`에서 가장 최근 4개 행을 조회하면 데이터가 없는 주(활동 없었던 주)를 건너뛰어 더 오래된 주를 당겨오는 문제가 있습니다.

```
# 잘못된 방식: ORDER BY year DESC, weekNumber DESC LIMIT 4
# weekly_stats에 없는 주가 있으면 그 주는 스킵되어 분모가 틀어짐
```

올바른 방식은 오늘 기준으로 4개의 캘린더 주 슬롯을 먼저 확정하고, 데이터가 없는 주는 `goalAchieved = false`로 채우는 것입니다.

```java
// StatsService.getCalendarWeeklyStats()
for (int i = 0; i < weeks; i++) {
    LocalDate weekDate = today.minusWeeks(i);
    int y = weekDate.get(IsoFields.WEEK_BASED_YEAR);
    int w = weekDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

    WeeklyStatResponse slot = weeklyStatRepository
            .findByUserIdAndYearAndWeekNumber(userId, y, w)
            .map(WeeklyStatResponse::new)
            .orElse(new WeeklyStatResponse(y, w)); // goalAchieved = false
    result.add(slot);
}
```

분모는 항상 4로 고정되어 "최근 4주 목표달성률 = 달성 주 수 / 4"가 정확하게 계산됩니다.

---

## 기술적 고민 2 — 스트릭 freeze 정책

주간 목표를 1회 달성하지 못했을 때 즉시 streak을 0으로 리셋하면 사용자 이탈로 이어질 수 있습니다.
**1회 유예(freeze)** 정책을 도입했습니다.

```
매주 월요일 streakStatsStep 실행:

직전 주 goal_achieved = true  → currentStreak + 1, freeze 해제
직전 주 goal_achieved = false (활동은 있었음):
    is_freezed = false → freeze 시작 (streak 유지, is_freezed = true)
    is_freezed = true  → 2주 연속 미달성 → streak 리셋 (currentStreak = 0)
```

freeze 상태에서 다음 주 목표를 달성하면 streak이 그대로 이어집니다.
2주 연속 미달성 시에만 streak을 리셋하여 사용자의 누적 기록을 최대한 보존합니다.

**목표 미설정 시 기본값**: 주간 목표를 설정하지 않은 경우 주 3개 완료를 기본 기준으로 적용합니다.

**활동 없는 주(빈 주) 처리**: 활동이 전혀 없는 주는 `weekly_stats` 레코드가 생성되지 않습니다.
`rebuildStreakFromHistory` 실행 시 연속된 두 레코드 간 주차 gap을 계산하여 빈 주를 감지합니다.

```
gap == 2 (빈 주 1개): freeze 유예 소비
gap >= 3 (빈 주 2개 이상): streak 리셋
```

---

## 기술적 고민 3 — 이번 주 streak 표시

streak_stats는 배치로 갱신되므로 이번 주가 아직 끝나지 않은 상태에서는 현재 주의 활동이 반영되지 않습니다.
이번 주 목표를 이미 달성했다면 `currentStreak + 1`을 응답에 반영합니다.

단순히 "완료한 이벤트가 있는지"가 아닌 **이번 주 실제 목표 달성 여부**를 기준으로 합니다.
완료 후 취소해도 `todo_events`에 COMPLETED 이벤트가 남기 때문에 이벤트 존재 여부로 판단하면 오동작합니다.

```java
int goal = weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(userId, year, week)
    .map(g -> g.getGoalCount())
    .orElse(3);
long completed = todoRepository.countByUserIdAndCompletedTrueAndCompletedAtBetween(userId, weekStart, weekEnd);
boolean goalAchieved = completed >= goal;

// StreakResponse 생성 시
this.currentStreak = stat.getCurrentStreak() + (goalAchieved ? 1 : 0);
this.longestStreak = Math.max(stat.getLongestStreak(), this.currentStreak);
```

---

## 기술적 고민 4 — 주간 목표 설정 시점 제한

주간 목표를 언제든 변경할 수 있으면 달성 직전에 목표를 낮추는 식의 어뷰징이 가능합니다.
**당주 월요일 또는 전주 일요일**에만 설정/수정할 수 있도록 서버에서 검증합니다.

```java
// 허용: 이번 주 월요일 ~ 일요일 23:59:59
// 허용: 다음 주 목표를 일요일에 미리 설정
// 차단: 그 외 시점 → GOAL_SETTING_LOCKED (400)
```

---

## Journey 달력 / 최근 7일 바차트

Journey 달력과 Keepers의 7일 바차트는 모두 `todos` 테이블을 직접 조회하여 날짜별 완료 건수를 반환합니다.
완료/취소 즉시 반영되며 배치 집계를 기다리지 않습니다.

```
GET /api/stats/calendar?year=2026&month=4  → 월별 달력
GET /api/stats/daily?days=6               → 최근 N일 바차트
  → todos WHERE user_id = ? AND completed_at BETWEEN ? AND ?
```

---

## 정리

| 고민 | 선택 |
|------|------|
| 실시간 집계 방식 | Redis Streams — 랭킹·주간통계 두 컨슈머 그룹에 fan-out |
| 최종 일관성 | Spring Batch — todo_events(source of truth) 기준 매일 재계산 |
| 이벤트 보존 | todo_events 원본 보존 — 언제든 전체 재계산 가능 |
| 스트릭 리셋 정책 | 1회 freeze 유예 — 사용자 기록 보존 |
| 이번 주 streak | 이번 주 완료 건수 vs 목표 비교 — 목표 달성 시에만 +1 표시 |
| 목표 설정 시점 | 서버 검증 — 어뷰징 차단 |
| 최근 4주 목표달성률 | 캘린더 주 슬롯 확정 후 fill — 분모 항상 4 보장 |
| ACK 실패 대응 | PEL 재처리 (idle 30s, 최대 3회) + 배치 최종 보정 |