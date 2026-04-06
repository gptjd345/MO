import { useState } from "react";
import { useLocation } from "wouter";
import { LayoutShell } from "@/components/layout-shell";
import { useCalendarStats, useStreak, useYearlyStats } from "@/hooks/use-stats";
import { useWeeklyGoal, useSetWeeklyGoal } from "@/hooks/use-weekly-goal";
import { Button } from "@/components/ui/button";
import { BicepsFlexed, ChevronLeft, ChevronRight, Flame } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

// ISO 주차 계산 유틸리티
function getISOWeek(date: Date): { year: number; week: number } {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return {
    year: d.getUTCFullYear(),
    week: Math.ceil((((d.getTime() - yearStart.getTime()) / 86400000) + 1) / 7),
  };
}

function getDayOfWeek(): string {
  const days = ["일", "월", "화", "수", "목", "금", "토"];
  return days[new Date().getDay()];
}

const LEVEL_COLORS: Record<number, string> = {
  0: '#3D3F6B',
  1: '#93C5FD',
  2: '#6EE7B7',
  3: '#FCD34D',
  4: '#F87171',
};

function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function getLevel(completedCount: number): number {
  if (completedCount === 0) return 0;
  if (completedCount === 1) return 1;
  if (completedCount === 2) return 2;
  if (completedCount === 3) return 3;
  return 4;
}

function MuscleIcon({ completedCount }: { completedCount: number }) {
  const level = getLevel(completedCount);
  const color = LEVEL_COLORS[level];
  return (
    <div
      className="rounded-md flex items-center justify-center"
      style={{ backgroundColor: hexToRgba(color, level === 0 ? 0.2 : 0.4), width: 24, height: 24 }}
      title={`${completedCount}개 완료`}
    >
      <BicepsFlexed style={{ color, width: 14, height: 14 }} strokeWidth={2} />
    </div>
  );
}

// ─── 월별 달력 ───────────────────────────────────────────────────────────────

function MonthlyCalendar() {
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth() + 1);
  const [, navigate] = useLocation();

  const { data: calendarData = [] } = useCalendarStats(viewYear, viewMonth);

  const completedByDate: Record<string, number> = {};
  for (const day of calendarData) {
    completedByDate[day.date] = day.completedCount;
  }

  const firstDay = new Date(viewYear, viewMonth - 1, 1);
  // ISO: 월요일=0 기준으로 offset 계산
  const startOffset = (firstDay.getDay() + 6) % 7;
  const daysInMonth = new Date(viewYear, viewMonth, 0).getDate();

  const prevMonth = () => {
    if (viewMonth === 1) { setViewYear(y => y - 1); setViewMonth(12); }
    else setViewMonth(m => m - 1);
  };
  const nextMonth = () => {
    if (viewMonth === 12) { setViewYear(y => y + 1); setViewMonth(1); }
    else setViewMonth(m => m + 1);
  };

  const monthLabel = `${viewYear}년 ${viewMonth}월`;
  const weekDays = ["월", "화", "수", "목", "금", "토", "일"];

  return (
    <div className="bg-card rounded-2xl border border-white/5 p-6">
      <div className="flex items-center justify-between mb-5">
        <h2 className="text-lg font-bold text-white">{monthLabel}</h2>
        <div className="flex gap-1">
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={prevMonth}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={nextMonth}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-7 mb-2">
        {weekDays.map(d => (
          <div key={d} className="text-center text-xs text-muted-foreground/60 font-medium py-1">{d}</div>
        ))}
      </div>

      <div className="grid grid-cols-7 gap-y-1">
        {/* 첫 주 빈 칸 */}
        {Array.from({ length: startOffset }).map((_, i) => (
          <div key={`empty-${i}`} />
        ))}

        {Array.from({ length: daysInMonth }).map((_, i) => {
          const day = i + 1;
          const dateStr = `${viewYear}-${String(viewMonth).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
          const completed = completedByDate[dateStr] ?? 0;
          const isToday = viewYear === today.getFullYear()
            && viewMonth === today.getMonth() + 1
            && day === today.getDate();

          const hasCompleted = completed > 0;
          return (
            <div
              key={day}
              onClick={() => hasCompleted && navigate(`/?completedDate=${dateStr}`)}
              className={`flex flex-col items-center py-1.5 rounded-lg transition-colors ${
                isToday ? "bg-primary/20 ring-2 ring-primary" : ""
              } ${hasCompleted ? "cursor-pointer hover:bg-white/5" : ""}`}
            >
              <span className={`text-xs mb-0.5 font-bold ${
                isToday ? "text-primary" : "text-muted-foreground/60"
              }`}>
                {day}
              </span>
              <MuscleIcon completedCount={completed} />
            </div>
          );
        })}
      </div>

      {/* 범례 */}
      <div className="flex items-center gap-3 mt-4 pt-4 border-t border-white/5">
        {([0, 1, 2, 3, 4] as const).map(n => (
          <div key={n} className="flex items-center gap-1.5">
            <MuscleIcon completedCount={n} />
            <span className="text-xs text-muted-foreground/50">{n === 4 ? "4+" : n}개</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── 연속 달성 현황 ────────────────────────────────────────────────────────────

function StreakSection() {
  const today = new Date();
  const { year: currentYear, week: currentWeek } = getISOWeek(today);
  const dayOfWeek = getDayOfWeek();
  const isSunday = today.getDay() === 0;
  const isMonday = today.getDay() === 1;

  const { data: streak } = useStreak();
  const { data: goalData } = useWeeklyGoal(currentYear, currentWeek);
  const setGoalMutation = useSetWeeklyGoal();
  const { toast } = useToast();

  const [inputGoal, setInputGoal] = useState<number | null>(null);
  const [showGoalInput, setShowGoalInput] = useState(false);

  const canSetGoal = isMonday || isSunday;

  // 일요일이면 다음 주 목표 설정 안내
  const nextWeekDate = new Date(today);
  nextWeekDate.setDate(today.getDate() + 7);
  const { year: nextYear, week: nextWeek } = getISOWeek(nextWeekDate);
  const { data: nextWeekGoalData } = useWeeklyGoal(
    isSunday ? nextYear : undefined,
    isSunday ? nextWeek : undefined
  );

  const handleSetGoal = async (goalCount: number) => {
    const targetYear = isSunday ? nextYear : currentYear;
    const targetWeek = isSunday ? nextWeek : currentWeek;

    const recommended = goalData?.recommended ?? 3;
    const isChallenge = goalCount > recommended;

    try {
      await setGoalMutation.mutateAsync({ year: targetYear, weekNumber: targetWeek, goalCount });
      toast({
        title: isChallenge ? "도전적인 목표네요! 할 수 있어요 💪" : "목표가 설정되었습니다",
        description: `주당 ${goalCount}개 완료를 목표로 설정했어요.`,
      });
      setShowGoalInput(false);
      setInputGoal(null);
    } catch {
      toast({ title: "목표 설정 실패", description: "다시 시도해주세요.", variant: "destructive" });
    }
  };

  const currentGoal = goalData?.hasGoal ? goalData.goal : null;
  const displayGoal = isSunday
    ? (nextWeekGoalData?.hasGoal ? nextWeekGoalData.goal : null)
    : currentGoal;
  const recommended = goalData?.recommended ?? 3;

  return (
    <div className="bg-card rounded-2xl border border-white/5 p-6">
      <div className="flex items-center gap-2 mb-4">
        <Flame className="h-5 w-5 text-orange-400" />
        <h2 className="text-lg font-bold text-white">연속 달성</h2>
      </div>

      {/* Streak 현황 */}
      <div className="grid grid-cols-2 gap-3 mb-5">
        <div className="bg-background/50 rounded-xl p-4 border border-white/5 text-center">
          <div className="flex items-center justify-center gap-1 mb-1">
            <span className="text-3xl font-bold text-white">{streak?.currentStreak ?? 0}</span>
            <span className="text-sm text-muted-foreground mt-2">주</span>
          </div>
          <p className="text-xs text-muted-foreground">현재 streak</p>
          {streak?.isFreezed && (
            <p className="text-xs text-orange-400 mt-1">⚠️ 유예 중</p>
          )}
        </div>
        <div className="bg-background/50 rounded-xl p-4 border border-white/5 text-center">
          <div className="flex items-center justify-center gap-1 mb-1">
            <span className="text-3xl font-bold text-white">{streak?.longestStreak ?? 0}</span>
            <span className="text-sm text-muted-foreground mt-2">주</span>
          </div>
          <p className="text-xs text-muted-foreground">최장 streak</p>
        </div>
      </div>

      {/* freeze 알림 */}
      {streak?.isFreezed && (
        <div className="mb-4 p-3 rounded-xl bg-orange-500/10 border border-orange-500/20 text-sm text-orange-300">
          이번 주 목표를 달성하면 streak이 이어져요! 🔥
        </div>
      )}

      {/* 일요일 다음 주 목표 미리 설정 안내 */}
      {isSunday && !nextWeekGoalData?.hasGoal && (
        <div className="mb-4 p-3 rounded-xl bg-primary/10 border border-primary/20 text-sm text-primary/80">
          다음 주 목표를 미리 설정해두세요 ✨
        </div>
      )}

      {/* 주간 목표 설정 */}
      <div className="border-t border-white/5 pt-4">
        <div className="flex items-center justify-between mb-2">
          <div>
            <p className="text-sm text-white font-medium">
              {isSunday ? "다음 주 목표" : "이번 주 목표"}
            </p>
            {displayGoal !== null ? (
              <p className="text-xs text-muted-foreground">주당 {displayGoal}개</p>
            ) : (
              <p className="text-xs text-muted-foreground/60">
                기본 목표 3개가 적용되고 있어요.{" "}
                {canSetGoal && !showGoalInput && (
                  <button
                    className="underline text-muted-foreground/80 hover:text-white transition-colors"
                    onClick={() => { setShowGoalInput(true); setInputGoal(recommended); }}
                  >
                    직접 설정하시겠어요?
                  </button>
                )}
              </p>
            )}
          </div>

          {canSetGoal && !showGoalInput && displayGoal !== null ? (
            <Button
              size="sm"
              variant="outline"
              className="text-xs h-7"
              onClick={() => { setShowGoalInput(true); setInputGoal(displayGoal ?? recommended); }}
            >
              수정
            </Button>
          ) : canSetGoal ? null : (
            <span className="text-xs text-muted-foreground/50">
              {dayOfWeek}요일 — 설정 잠김
            </span>
          )}
        </div>

        {showGoalInput && canSetGoal && (
          <div className="mt-3 p-3 bg-background/50 rounded-xl border border-white/5">
            <p className="text-xs text-muted-foreground mb-2">
              최근 3주 평균 기준 추천 목표는 <span className="text-primary font-medium">{recommended}개</span>예요
            </p>
            <div className="flex gap-2 flex-wrap">
              {[1, 2, 3, 4, 5, 6, 7].map(n => (
                <button
                  key={n}
                  onClick={() => setInputGoal(n)}
                  className={`w-9 h-9 rounded-lg text-sm font-medium border transition-colors ${
                    inputGoal === n
                      ? "bg-primary text-white border-primary"
                      : n === recommended
                      ? "border-primary/40 text-primary/80 bg-primary/10"
                      : "border-white/10 text-muted-foreground hover:border-white/20 hover:text-white"
                  }`}
                >
                  {n}
                </button>
              ))}
            </div>
            <div className="flex gap-2 mt-3">
              <Button
                size="sm"
                className="flex-1 h-8"
                disabled={inputGoal === null || setGoalMutation.isPending}
                onClick={() => inputGoal !== null && handleSetGoal(inputGoal)}
              >
                저장
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="h-8"
                onClick={() => { setShowGoalInput(false); setInputGoal(null); }}
              >
                취소
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── 연간 잔디 ───────────────────────────────────────────────────────────────

const MONTH_LABELS = ["1월","2월","3월","4월","5월","6월","7월","8월","9월","10월","11월","12월"];

function YearlyHeatmap() {
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const { data: yearlyData = [] } = useYearlyStats(viewYear);

  // date → completedCount 맵
  const countByDate: Record<string, number> = {};
  for (const d of yearlyData) {
    countByDate[d.date] = d.completedCount;
  }

  // 해당 연도의 1월1일 ~ 12월31일 셀 배열 생성
  // 열(column) = 주 단위, 행(row) = 요일(월~일)
  const jan1 = new Date(viewYear, 0, 1);
  // 월요일 기준 offset (0=월 … 6=일)
  const startOffset = (jan1.getDay() + 6) % 7;
  const isLeap = (viewYear % 4 === 0 && viewYear % 100 !== 0) || viewYear % 400 === 0;
  const totalDays = isLeap ? 366 : 365;
  const totalCells = startOffset + totalDays;
  const totalWeeks = Math.ceil(totalCells / 7);

  // 각 주의 1번째 날(월요일)이 속한 월 → 월 라벨 위치 계산
  const monthColStart: { month: number; col: number }[] = [];
  for (let col = 0; col < totalWeeks; col++) {
    const firstCellIndex = col * 7 - startOffset;
    if (firstCellIndex < 0 || firstCellIndex >= totalDays) continue;
    const d = new Date(viewYear, 0, 1 + firstCellIndex);
    if (d.getDate() <= 7 && (col === 0 || d.getMonth() !== new Date(viewYear, 0, 1 + ((col - 1) * 7 - startOffset)).getMonth())) {
      monthColStart.push({ month: d.getMonth(), col });
    }
  }

  const totalCompleted = yearlyData.reduce((s, d) => s + d.completedCount, 0);
  const activeDays = yearlyData.filter(d => d.completedCount > 0).length;

  return (
    <div className="bg-card rounded-2xl border border-white/5 p-6">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-bold text-white">{viewYear}년 기여도</h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            {activeDays}일 활동 · 총 {totalCompleted}개 완료
          </p>
        </div>
        <div className="flex gap-1">
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => setViewYear(y => y - 1)}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost" size="icon" className="h-8 w-8"
            onClick={() => setViewYear(y => y + 1)}
            disabled={viewYear >= today.getFullYear()}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <div style={{ minWidth: totalWeeks * 14 }}>
          {/* 월 라벨 */}
          <div className="relative h-5 mb-1" style={{ display: 'grid', gridTemplateColumns: `repeat(${totalWeeks}, 12px)`, gap: 2 }}>
            {monthColStart.map(({ month, col }) => (
              <div
                key={month}
                className="text-xs text-muted-foreground/60 absolute"
                style={{ left: col * 14 }}
              >
                {MONTH_LABELS[month]}
              </div>
            ))}
          </div>

          {/* 잔디 그리드 */}
          <div style={{ display: 'grid', gridTemplateColumns: `repeat(${totalWeeks}, 12px)`, gridTemplateRows: 'repeat(7, 12px)', gap: 2, gridAutoFlow: 'column' }}>
            {/* 앞 빈칸 */}
            {Array.from({ length: startOffset }).map((_, i) => (
              <div key={`pre-${i}`} />
            ))}
            {/* 실제 날짜 셀 */}
            {Array.from({ length: totalDays }).map((_, i) => {
              const d = new Date(viewYear, 0, 1 + i);
              const dateStr = `${viewYear}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
              const count = countByDate[dateStr] ?? 0;
              const level = count === 0 ? 0 : count === 1 ? 1 : count === 2 ? 2 : count === 3 ? 3 : 4;
              const color = LEVEL_COLORS[level];
              const isToday = dateStr === `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

              return (
                <div
                  key={dateStr}
                  title={`${dateStr}: ${count}개 완료`}
                  style={{
                    width: 12,
                    height: 12,
                    borderRadius: 3,
                    backgroundColor: hexToRgba(color, level === 0 ? 0.2 : 0.75),
                    outline: isToday ? `1.5px solid ${color}` : undefined,
                  }}
                />
              );
            })}
          </div>
        </div>
      </div>

      {/* 범례 */}
      <div className="flex items-center gap-2 mt-4 pt-3 border-t border-white/5">
        <span className="text-xs text-muted-foreground/50 mr-1">적음</span>
        {([0, 1, 2, 3, 4] as const).map(level => (
          <div
            key={level}
            style={{ width: 12, height: 12, borderRadius: 3, backgroundColor: hexToRgba(LEVEL_COLORS[level], level === 0 ? 0.2 : 0.75) }}
          />
        ))}
        <span className="text-xs text-muted-foreground/50 ml-1">많음</span>
      </div>
    </div>
  );
}

// ─── 캘린더 페이지 ────────────────────────────────────────────────────────────

export default function CalendarPage() {
  return (
    <LayoutShell>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white">Journey</h1>
        <p className="text-sm text-muted-foreground mt-1">내가 쌓아온 것들을 돌아봐요</p>
      </div>

      <div className="space-y-6">
        <MonthlyCalendar />
        <StreakSection />
        <YearlyHeatmap />
      </div>
    </LayoutShell>
  );
}
