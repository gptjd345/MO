# 통계 시스템 설계 (Journey)

## 개요

일정 완료 이력을 기반으로 일별/주별 달성률, 주간 스트릭, Journey 달력을 제공하는 통계 시스템입니다.
실시간 집계 대신 **이벤트 소싱 + 배치 집계** 구조를 채택했습니다.

---

## 왜 실시간 집계를 쓰지 않았는가

일정 완료 시 `SELECT COUNT(*) FROM todos WHERE user_id = ? AND completed_at = ?` 형태의 집계를
매 요청마다 실행하면 todo 수가 늘수록 쿼리 비용이 증가합니다.

통계 데이터는 **변경 빈도가 낮고 조회 빈도가 높은** 특성이 있습니다.
배치로 사전 계산하여 `daily_stats`, `weekly_stats`에 저장하고 조회 시 단순 SELECT로 응답하는 구조를 선택했습니다.

---

## 이벤트 소싱

완료/취소 이벤트를 `todo_events` 테이블에 원본 그대로 보존합니다.

```
일정 완료 → todo_events INSERT (COMPLETED, event_date)
일정 취소 → todo_events INSERT (CANCELLED, event_date)
```

이벤트 원본을 보존하는 이유:
- 배치가 실패하거나 스키마가 변경되어도 `todo_events`를 소스로 삼아 전체 stats를 언제든 재계산할 수 있습니다.
- `DevController /dev/batch/recalculateStats`는 모든 이벤트를 순회하며 stats를 전체 재구성합니다.

---

## Spring Batch 구성

`DailyStatsBatchJob`은 매일 새벽 1시에 cron으로 실행되며 3개의 Step이 순서대로 동작합니다.

```
statsJob
  ├── Step 1: dailyStatsStep  — 어제 기준 daily_stats 집계
  ├── Step 2: weeklyStatsStep — 어제 기준 weekly_stats 집계
  └── Step 3: streakStatsStep — 월요일에만 실행, 직전 주 streak 업데이트
```

각 Step은 Spring Batch의 **Tasklet** 방식으로 구현했습니다.
Chunk 방식은 대용량 데이터를 페이지 단위로 처리할 때 적합하지만,
이 시스템은 하루치 이벤트를 한 번에 집계하는 구조라 Tasklet이 더 단순하고 명확합니다.

---

## 기술적 고민 1 — daily_stats 집계 로직

단순히 `completed_at = 어제`인 건수만 세면 "예정된 일정 대비 달성률"을 정확히 계산할 수 없습니다.
달성률의 분모(예정 수)를 올바르게 계산하기 위해 두 케이스를 구분했습니다.

```
completedCount  = completedAt = 날짜 인 todos 수

scheduledCount  = (endDate = 날짜 AND completedAt IS NULL)  ← 마감일이 오늘인데 못 한 것
               + (completedAt = 날짜 AND endDate != 날짜)   ← 오늘 완료했지만 마감일이 오늘이 아닌 것

onTimeDoneCount = endDate = 날짜 AND completedAt = 날짜      ← 마감일 당일 완료

totalScheduled  = scheduledCount + onTimeDoneCount
completionRate  = completedCount / totalScheduled
```

이 계산은 `TodoRepository`에 두기엔 배치 전용 로직이라 `EntityManager`로 직접 JPQL을 작성했습니다.

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

---

## 기술적 고민 3 — 이번 주 streak 표시

streak_stats는 배치로 갱신되므로 이번 주가 아직 끝나지 않은 상태에서는 현재 주의 활동이 반영되지 않습니다.
이번 주에 완료한 일정이 있다면 `currentStreak + 1`을 응답에 반영해야 합니다.

```java
// StatsService
boolean currentWeekActive = todoEventRepository
    .existsByUserIdAndEventTypeAndEventDateBetween(userId, "COMPLETED", weekStart, weekEnd);

// StreakResponse 생성 시
this.currentStreak = stat.getCurrentStreak() + (currentWeekActive ? 1 : 0);
this.longestStreak = Math.max(stat.getLongestStreak(), this.currentStreak);
```

DB에서 이번 주 이벤트 존재 여부만 확인하므로 조회 비용이 낮습니다.

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

## Journey 달력 연동

Journey 달력은 `daily_stats`를 기반으로 날짜별 완료 건수와 달성률을 시각화합니다.

```
GET /api/stats/calendar?year=2026&month=4
  → daily_stats WHERE user_id = ? AND stat_date BETWEEN ? AND ?
  → 날짜별 completedCount, completionRate 반환
```

달력에서 완료 건이 있는 날짜를 클릭하면 Dashboard로 이동하여 해당 날짜의 완료 일정을 하이라이트합니다.

```
calendar-page: onClick → navigate(`/?completedDate=2026-04-05`)
dashboard-page: completedDate 쿼리 파라미터 → completedTodos에서 completedAt이 일치하는 카드 하이라이트
```

---

## 정리

| 고민 | 선택 |
|------|------|
| 실시간 집계 vs 배치 집계 | 배치 — 조회 성능 우선, 통계는 분 단위 지연 허용 |
| 이벤트 보존 방식 | todo_events 원본 보존 — 언제든 재계산 가능 |
| 스트릭 리셋 정책 | 1회 freeze 유예 — 사용자 기록 보존 |
| 이번 주 streak | DB 실시간 조회 + 배치 결과에 +1 — 경량 쿼리로 최신성 보장 |
| 목표 설정 시점 | 서버 검증 — 어뷰징 차단 |