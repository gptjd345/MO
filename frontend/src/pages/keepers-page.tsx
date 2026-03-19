import { useMemo } from "react";
import { LayoutShell } from "@/components/layout-shell";
import { useAuth } from "@/hooks/use-auth";
import { useRanking, RankingEntry } from "@/hooks/use-ranking";
import { motion } from "framer-motion";
import { Star, Heart } from "lucide-react";

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

        {/* Empty state */}
        {!isLoading && rankings.length === 0 && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-center py-16 text-muted-foreground text-sm"
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
            {/* Above */}
            {above.map((entry) => (
              <motion.div key={entry.userId} variants={item}>
                <KeeperCard entry={entry} scoreBar={scoreBar(entry.score)} />
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
            {me && (
              <motion.div variants={item}>
                <CurrentUserCard user={user} scoreBar={scoreBar(me.score)} />
              </motion.div>
            )}

            {/* Subtle divider */}
            {below.length > 0 && (
              <motion.div variants={item} className="py-1">
                <div className="w-full border-t border-white/5" />
              </motion.div>
            )}

            {/* Below */}
            {below.map((entry) => (
              <motion.div key={entry.userId} variants={item}>
                <KeeperCard entry={entry} scoreBar={scoreBar(entry.score)} />
              </motion.div>
            ))}
          </motion.div>
        )}

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
