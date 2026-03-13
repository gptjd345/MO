import { useMemo } from "react";
import { LayoutShell } from "@/components/layout-shell";
import { useAuth } from "@/hooks/use-auth";
import { motion } from "framer-motion";
import { Star, Heart } from "lucide-react";

interface KeeperEntry {
  id: number;
  initial: string;
  score: number;
  completionRate: number;
  avatarGradient: string;
}

const ABOVE_TEMPLATES = [
  { id: 1, initial: "S", completionRate: 94, avatarGradient: "from-violet-500 to-purple-600" },
  { id: 2, initial: "J", completionRate: 89, avatarGradient: "from-blue-500 to-cyan-600" },
  { id: 3, initial: "M", completionRate: 83, avatarGradient: "from-teal-500 to-emerald-600" },
];

const BELOW_TEMPLATES = [
  { id: 4, initial: "K", completionRate: 74, avatarGradient: "from-rose-400 to-pink-600" },
  { id: 5, initial: "A", completionRate: 67, avatarGradient: "from-amber-400 to-orange-500" },
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
  const userScore = user?.score ?? 0;

  const above: KeeperEntry[] = useMemo(
    () =>
      ABOVE_TEMPLATES.map((t, i) => ({
        ...t,
        score: userScore + (3 - i) * 12,
      })),
    [userScore]
  );

  const below: KeeperEntry[] = useMemo(
    () =>
      BELOW_TEMPLATES.map((t, i) => ({
        ...t,
        score: Math.max(0, userScore - (i + 1) * 12),
      })),
    [userScore]
  );

  const userCompletionRate = 80; // Redis 연동 후 실 데이터로 교체

  return (
    <LayoutShell>
      <div className="max-w-[520px] mx-auto py-4 md:py-6">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="mb-10"
        >
          <div className="flex items-center gap-3 mb-4">
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

          <p className="text-sm text-muted-foreground leading-relaxed">
            지금 이 순간에도, 나처럼 스스로와의 약속을 지키고 있는 사람들이에요.
            <br />
            순위가 아닌,{" "}
            <span className="text-amber-400/80 font-medium">함께라는 것</span>에
            의미가 있어요.
          </p>
        </motion.div>

        {/* List */}
        <motion.div
          variants={container}
          initial="hidden"
          animate="show"
          className="space-y-2.5"
        >
          {/* Above */}
          {above.map((keeper) => (
            <motion.div key={keeper.id} variants={item}>
              <KeeperCard keeper={keeper} />
            </motion.div>
          ))}

          {/* "나" divider */}
          <motion.div variants={item} className="relative py-2">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-amber-500/15" />
            </div>
            <div className="relative flex justify-center">
              <span className="bg-background px-3 text-[11px] text-amber-400/60 tracking-wide">
                지금 나의 자리
              </span>
            </div>
          </motion.div>

          {/* Current user */}
          <motion.div variants={item}>
            <CurrentUserCard user={user} completionRate={userCompletionRate} />
          </motion.div>

          {/* Subtle divider */}
          <motion.div variants={item} className="py-1">
            <div className="w-full border-t border-white/5" />
          </motion.div>

          {/* Below */}
          {below.map((keeper) => (
            <motion.div key={keeper.id} variants={item}>
              <KeeperCard keeper={keeper} />
            </motion.div>
          ))}
        </motion.div>

        {/* Footer */}
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.85, duration: 0.5 }}
          className="mt-10 text-center text-[11px] text-muted-foreground/35 italic"
        >
          함께 약속을 지키면, 더 멀리 갈 수 있어요
        </motion.p>
      </div>
    </LayoutShell>
  );
}

function KeeperCard({ keeper }: { keeper: KeeperEntry }) {
  return (
    <div className="flex items-center gap-4 px-4 py-3.5 rounded-2xl border border-white/[0.07] bg-card/50 backdrop-blur-sm transition-colors duration-200 hover:border-white/[0.12] hover:bg-card/70">
      <div
        className={`h-10 w-10 rounded-full bg-gradient-to-br ${keeper.avatarGradient} flex items-center justify-center flex-shrink-0 opacity-75`}
      >
        <span className="text-sm font-bold text-white">{keeper.initial}</span>
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between mb-1.5">
          <span className="text-sm text-white/65 font-medium">
            {keeper.initial}***
          </span>
          <div className="flex items-center gap-1">
            <Star className="h-3 w-3 text-yellow-400 fill-yellow-400" />
            <span className="text-xs text-yellow-400/75 font-medium">
              {keeper.score}점
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <div className="flex-1 h-1.5 rounded-full bg-white/5 overflow-hidden">
            <div
              className="h-full rounded-full bg-gradient-to-r from-primary/45 to-purple-400/45"
              style={{ width: `${keeper.completionRate}%` }}
            />
          </div>
          <span className="text-[11px] text-muted-foreground/50 flex-shrink-0 w-7 text-right">
            {keeper.completionRate}%
          </span>
        </div>
        <p className="text-[10px] text-muted-foreground/30 mt-0.5">
          약속 이행률
        </p>
      </div>
    </div>
  );
}

function CurrentUserCard({
  user,
  completionRate,
}: {
  user: { email: string; nickname: string; score: number; plan: string } | null;
  completionRate: number;
}) {
  const displayName = user?.nickname ?? user?.email?.split("@")[0] ?? "나";
  const initial = displayName.charAt(0).toUpperCase();

  return (
    <div className="relative flex items-center gap-4 px-5 py-4 rounded-2xl border border-amber-500/25 bg-amber-500/[0.04]">
      {/* Subtle inner glow */}
      <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-amber-500/[0.04] via-transparent to-rose-500/[0.04] pointer-events-none" />

      {/* "나" badge */}
      <span className="absolute -top-[11px] left-4 text-[11px] px-2.5 py-[3px] rounded-full bg-amber-500/15 border border-amber-500/25 text-amber-400 font-medium tracking-wide">
        나
      </span>

      {/* Avatar */}
      <div className="relative flex-shrink-0">
        <div className="h-12 w-12 rounded-full bg-gradient-to-br from-amber-500/30 to-rose-500/25 border border-amber-500/30 flex items-center justify-center">
          <span className="text-base font-bold text-amber-200">{initial}</span>
        </div>
        <div className="absolute inset-[-3px] rounded-full ring-1 ring-amber-500/15 pointer-events-none" />
      </div>

      {/* Info */}
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
              style={{ width: `${completionRate}%` }}
            />
          </div>
          <span className="text-xs text-amber-400/65 flex-shrink-0 w-7 text-right">
            {completionRate}%
          </span>
        </div>
        <p className="text-[10px] text-amber-400/35 mt-1">약속 이행률</p>
      </div>
    </div>
  );
}
