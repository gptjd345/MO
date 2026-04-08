import { useMemo } from "react";
import { LayoutShell } from "@/components/layout-shell";
import { useAuth } from "@/hooks/use-auth";
import { useRanking, RankingEntry } from "@/hooks/use-ranking";
import { useStreak, useWeeklyStats, useCalendarStats, useDailyStats, CalendarDay } from "@/hooks/use-stats";
import { motion } from "framer-motion";
import { Star, Heart, Flame } from "lucide-react";

const GRADIENTS = [
  "from-violet-500 to-purple-600",
  "from-blue-500 to-cyan-600",
  "from-teal-500 to-emerald-600",
  "from-rose-400 to-pink-600",
  "from-amber-400 to-orange-500",
  "from-indigo-500 to-blue-600",
  "from-pink-500 to-rose-600",
];

const container = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.09 } },
};

const item = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.38, ease: "easeOut" } },
};

export default function KeepersPage() {
  const { user } = useAuth();
  const { data, isLoading } = useRanking();

  const rankings = data?.rankings ?? [];

  const meIndex = rankings.findIndex((r) => r.isMe);
  const above = meIndex > -1 ? rankings.slice(0, meIndex) : [];
  const me = meIndex > -1 ? rankings[meIndex] : null;
  const below = meIndex > -1 ? rankings.slice(meIndex + 1) : [];

  const maxScore = useMemo(
    () => Math.max(...rankings.map((r) => r.score), 1),
    [rankings]
  );

  const scoreBar = (score: number) =>
    Math.max(Math.round((score / maxScore) * 100), 4);

  return (
    <LayoutShell>
      <div className="max-w-[520px] mx-auto py-4 md:py-6">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="mb-8"
        >
          <div className="flex items-center gap-3 mb-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-amber-500/20 to-rose-500/20 border border-amber-500/20 flex items-center justify-center flex-shrink-0">
              <Heart
                className="h-5 w-5 text-amber-400"
                style={{ fill: "rgba(251,191,36,0.18)" }}
              />
            </div>
            <div>
              <h1 className="text-2xl font-bold font-display text-white leading-tight">
                Keepers
              </h1>
              <p className="text-xs text-muted-foreground mt-0.5">
                약속을 함께 지켜가는 사람들
              </p>
            </div>
          </div>
        </motion.div>

        {/* Momentum Section */}
        <MomentumSection />

        {/* Ranking Header */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.3, duration: 0.4 }}
          className="mb-3 mt-10"
        >
          <p className="text-[13px] text-white/95 tracking-wide text-center">
            지금 나와 함께하는 사람들
          </p>
        </motion.div>

        {/* Empty state */}
        {!isLoading && rankings.length === 0 && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-center py-12 text-muted-foreground text-sm"
          >
            <Heart className="h-8 w-8 mx-auto mb-3 text-amber-400/30" />
            <p>일정을 완료하면 랭킹에 등록돼요</p>
          </motion.div>
        )}

        {/* List */}
        {rankings.length > 0 && (
          <motion.div
            variants={container}
            initial="hidden"
            animate="show"
            className="space-y-2.5"
          >
            {above.map((entry) => (
              <motion.div key={entry.userId} variants={item}>
                <KeeperCard entry={entry} scoreBar={scoreBar(entry.score)} />
              </motion.div>
            ))}


            {me && (
              <motion.div variants={item}>
                <CurrentUserCard user={user} scoreBar={scoreBar(me.score)} />
              </motion.div>
            )}

            {below.length > 0 && (
              <motion.div variants={item} className="py-1">
                <div className="w-full border-t border-white/5" />
              </motion.div>
            )}

            {below.map((entry) => (
              <motion.div key={entry.userId} variants={item}>
                <KeeperCard entry={entry} scoreBar={scoreBar(entry.score)} />
              </motion.div>
            ))}
          </motion.div>
        )}

        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.85, duration: 0.5 }}
          className="mt-10 text-center text-[11px] text-muted-foreground/95 italic"
        >
          함께 약속을 지키면, 더 멀리 갈 수 있어요
        </motion.p>
      </div>
    </LayoutShell>
  );
}

// ─── Momentum Section ────────────────────────────────────────────────────────

const MOTIVATION: Record<string, string> = {
  zero:    "첫 번째 완료가 속도를 만들어요",
  low:     "조금씩 속도가 붙고 있어요",
  mid:     "좋은 흐름이에요, 계속 가요",
  high:    "거의 다 왔어요, 멈추지 마세요",
  done:    "이번 주 모멘텀을 완성했어요 ✨",
};

function getMotivation(progress: number) {
  if (progress === 0)   return MOTIVATION.zero;
  if (progress < 34)   return MOTIVATION.low;
  if (progress < 67)   return MOTIVATION.mid;
  if (progress < 100)  return MOTIVATION.high;
  return MOTIVATION.done;
}

/** toISOString()은 UTC 기준이므로 로컬 날짜로 포맷 */
function toLocalDateStr(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function MomentumSection() {
  const { data: streak } = useStreak();
  const { data: weeklyStats = [] } = useWeeklyStats(4);
  const { data: dailyStats = [] } = useDailyStats(6); // 어제부터 6일 전까지 (배치)

  const achievedCount = weeklyStats.filter((w) => w.goalAchieved).length;
  const weeklyAchieveRate =
    weeklyStats.length > 0
      ? Math.round((achievedCount / 4) * 100)
      : null;

  const now = new Date();
  const { data: calendarData = [] } = useCalendarStats(
    now.getFullYear(),
    now.getMonth() + 1
  );

  const todayStr = toLocalDateStr(now);
  const todayCount =
    calendarData.find((d) => d.date === todayStr)?.completedCount ?? 0;

  const currentWeekCompleted = streak?.currentWeekCompleted ?? 0;
  const currentWeekGoal = streak?.currentWeekGoal ?? 3;
  const progress = currentWeekGoal > 0
    ? Math.min((currentWeekCompleted / currentWeekGoal) * 100, 100)
    : 0;

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.1 }}
      className="rounded-2xl border border-amber-500/15 bg-gradient-to-br from-amber-500/[0.05] via-transparent to-rose-500/[0.04] p-5"
    >
      {/* 헤더 */}
      <div className="flex items-center gap-2 mb-1">
        <Flame className="h-4 w-4 text-amber-400" />
        <span className="text-sm font-semibold text-white/80">나의 모멘텀</span>
        {weeklyAchieveRate !== null && (
          <span className="ml-auto text-xs text-white/50">
            최근 4주 목표달성률{" "}
            <span className="text-amber-400 font-medium">{weeklyAchieveRate}%</span>
          </span>
        )}
      </div>
      <p className="text-[11px] text-white/60 mb-5 pl-6">
        하나씩 쌓이면, 멈추지 않게 됩니다
      </p>

      {/* 메인: 속도계 */}
      <SpeedometerGauge value={currentWeekCompleted} max={currentWeekGoal} />

      {/* 동기 카피 */}
      <motion.p
        key={Math.floor(progress / 34)}
        initial={{ opacity: 0, y: 4 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center text-xs text-amber-300/80 mt-2 mb-5"
      >
        {getMotivation(progress)}
      </motion.p>

      {/* 보조: 최근 7일 바 차트 */}
      <DailyBars dailyStats={dailyStats} todayCount={todayCount} todayStr={todayStr} />

      {/* 하단 스탯 카드 */}
      <div className="grid grid-cols-2 gap-3 mt-4">
        <StatCard label="오늘 완료" value={`${todayCount}개`} />
        <RemainingCard completed={currentWeekCompleted} goal={currentWeekGoal} />
      </div>
    </motion.div>
  );
}

// ─── Speedometer SVG ─────────────────────────────────────────────────────────

function SpeedometerGauge({ value, max }: { value: number; max: number }) {
  const progress = max > 0 ? Math.min((value / max) * 100, 100) : 0;
  const r = 80;
  const cx = 100;
  const cy = 92;
  const arcLength = Math.PI * r;
  const dashOffset = arcLength * (1 - progress / 100);

  return (
    <div className="flex flex-col items-center">
      <svg viewBox="0 0 200 100" className="w-full max-w-[200px]">
        <defs>
          <linearGradient id="momentumGrad" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#FBBF24" />
            <stop offset="55%" stopColor="#F97316" />
            <stop offset="100%" stopColor="#F43F5E" />
          </linearGradient>
        </defs>

        {/* 배경 트랙 */}
        <path
          d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`}
          fill="none"
          stroke="rgba(255,255,255,0.15)"
          strokeWidth="13"
          strokeLinecap="round"
        />

        {/* 진행 호 */}
        <motion.path
          d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`}
          fill="none"
          stroke="url(#momentumGrad)"
          strokeWidth="13"
          strokeLinecap="round"
          strokeDasharray={arcLength}
          initial={{ strokeDashoffset: arcLength }}
          animate={{ strokeDashoffset: dashOffset }}
          transition={{ duration: 1.5, ease: "easeOut" }}
        />

        {/* 끝단 glow */}
        {progress > 2 && (
          <motion.circle
            r="5"
            fill="rgba(249,115,22,0.35)"
            cx={cx - r * Math.cos((progress / 100) * Math.PI)}
            cy={cy - r * Math.sin((progress / 100) * Math.PI)}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 1.5, delay: 0.5 }}
          />
        )}

        {/* 퍼센트 */}
        <text x={cx} y={cy - 20} textAnchor="middle" fill="white" fontSize="32" fontWeight="700">
          {Math.round(progress)}%
        </text>
        <text x={cx} y={cy - 4} textAnchor="middle" fill="rgba(255,255,255,0.65)" fontSize="10">
          이번 주 달성률
        </text>
      </svg>
    </div>
  );
}

// ─── Daily Bar Chart ──────────────────────────────────────────────────────────

function DailyBars({
  dailyStats,
  todayCount,
  todayStr,
}: {
  dailyStats: CalendarDay[];
  todayCount: number;
  todayStr: string;
}) {
  // 6일 전~어제: daily_stats / 오늘: 실시간
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (6 - i));
    const dateStr = toLocalDateStr(d);
    const isToday = dateStr === todayStr;
    const stat = dailyStats.find((s) => s.date === dateStr);
    const count = isToday ? todayCount : (stat?.completedCount ?? 0);
    const label = ["일", "월", "화", "수", "목", "금", "토"][d.getDay()];
    return { dateStr, count, isToday, label };
  });

  const maxCount = Math.max(...days.map((d) => d.count), 1);
  const BAR_MAX_PX = 36;

  return (
    <div>
      <p className="text-[10px] text-white/90 mb-2">최근 7일</p>
      <div className="flex items-end gap-1" style={{ height: `${BAR_MAX_PX}px` }}>
        {days.map((d, i) => {
          const barHeight = Math.max((d.count / maxCount) * BAR_MAX_PX, 3);
          return (
            <motion.div
              key={d.dateStr}
              className="flex-1 rounded-t-sm"
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: barHeight, opacity: 1 }}
              transition={{ duration: 0.45, delay: i * 0.06, ease: "easeOut" }}
              style={{
                background: d.isToday
                  ? "rgba(251,191,36,0.75)"
                  : d.count > 0
                  ? "rgba(251,191,36,0.5)"
                  : "rgba(255,255,255,0.3)",
              }}
            />
          );
        })}
      </div>
      <div className="flex gap-1 mt-1">
        {days.map((d) => (
          <span
            key={d.dateStr}
            className={`flex-1 text-center text-[9px] ${
              d.isToday ? "text-amber-300" : "text-white/85"
            }`}
          >
            {d.isToday ? "오늘" : d.label}
          </span>
        ))}
      </div>
    </div>
  );
}

// ─── Stat Cards ──────────────────────────────────────────────────────────────

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-white/[0.12] bg-white/[0.07] px-4 py-3.5">
      <p className="text-[11px] text-white/60 mb-1.5">{label}</p>
      <p className="text-xl font-bold text-white">{value}</p>
    </div>
  );
}

function RemainingCard({
  completed,
  goal,
}: {
  completed: number;
  goal: number;
}) {
  const remaining = Math.max(0, goal - completed);
  const done = remaining === 0;

  return (
    <div
      className={`rounded-xl border px-4 py-3.5 ${
        done
          ? "border-amber-500/40 bg-amber-500/[0.1]"
          : "border-white/[0.12] bg-white/[0.07]"
      }`}
    >
      <p className="text-[11px] text-white/60 mb-1.5">이번 주 목표까지</p>
      {done ? (
        <p className="text-xl font-bold text-amber-400">달성 완료 🎉</p>
      ) : (
        <p className="text-xl font-bold text-white">
          <span className="text-amber-400">{remaining}개</span> 남았어요
        </p>
      )}
    </div>
  );
}

// ─── Ranking Cards ────────────────────────────────────────────────────────────

function KeeperCard({ entry, scoreBar }: { entry: RankingEntry; scoreBar: number }) {
  const initial = entry.nickname.charAt(0).toUpperCase();
  const gradient = GRADIENTS[entry.userId % GRADIENTS.length];

  return (
    <div className="flex items-center gap-4 px-4 py-3.5 rounded-2xl border border-white/[0.07] bg-card/50 backdrop-blur-sm transition-colors duration-200 hover:border-white/[0.12] hover:bg-card/70">
      <div
        className={`h-10 w-10 rounded-full bg-gradient-to-br ${gradient} flex items-center justify-center flex-shrink-0 opacity-75`}
      >
        <span className="text-sm font-bold text-white">{initial}</span>
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between mb-1.5">
          <span className="text-sm text-white/65 font-medium">
            {entry.nickname}
          </span>
          <div className="flex items-center gap-1">
            <Star className="h-3 w-3 text-yellow-400 fill-yellow-400" />
            <span className="text-xs text-yellow-400/75 font-medium">
              {entry.score}점
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <div className="flex-1 h-1.5 rounded-full bg-white/5 overflow-hidden">
            <div
              className="h-full rounded-full bg-gradient-to-r from-primary/45 to-purple-400/45"
              style={{ width: `${scoreBar}%` }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function CurrentUserCard({
  user,
  scoreBar,
}: {
  user: { email: string; nickname: string; score: number; plan: string } | null;
  scoreBar: number;
}) {
  const displayName = user?.nickname ?? user?.email?.split("@")[0] ?? "나";
  const initial = displayName.charAt(0).toUpperCase();

  return (
    <div className="relative flex items-center gap-4 px-5 py-4 rounded-2xl border border-amber-500/25 bg-amber-500/[0.04]">
      <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-amber-500/[0.04] via-transparent to-rose-500/[0.04] pointer-events-none" />

      <span className="absolute -top-[11px] left-4 text-[11px] px-2.5 py-[3px] rounded-full bg-amber-500/15 border border-amber-500/25 text-amber-400 font-medium tracking-wide">
        나
      </span>

      <div className="relative flex-shrink-0">
        <div className="h-12 w-12 rounded-full bg-gradient-to-br from-amber-500/30 to-rose-500/25 border border-amber-500/30 flex items-center justify-center">
          <span className="text-base font-bold text-amber-200">{initial}</span>
        </div>
        <div className="absolute inset-[-3px] rounded-full ring-1 ring-amber-500/15 pointer-events-none" />
      </div>

      <div className="flex-1 min-w-0 relative">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-semibold text-white/90">{displayName}</span>
          <div className="flex items-center gap-1">
            <Star className="h-3.5 w-3.5 text-yellow-400 fill-yellow-400" />
            <span className="text-sm text-yellow-400 font-bold">
              {user?.score ?? 0}점
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <div className="flex-1 h-2 rounded-full bg-white/5 overflow-hidden">
            <div
              className="h-full rounded-full bg-gradient-to-r from-amber-400 to-rose-400"
              style={{ width: `${scoreBar}%` }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}