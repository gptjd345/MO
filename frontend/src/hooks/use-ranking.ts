import { useQuery } from "@tanstack/react-query";
import { getToken, tryRefresh } from "@/lib/queryClient";

export interface RankingEntry {
  userId: number;
  nickname: string;
  score: number;
  rank: number;
  isMe: boolean;
}

async function fetchRankings(): Promise<{ rankings: RankingEntry[] }> {
  const makeHeaders = (): Record<string, string> => {
    const token = getToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  };

  let res = await fetch("/api/rankings/around", { headers: makeHeaders() });
  if (res.status === 401) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      res = await fetch("/api/rankings/around", { headers: makeHeaders() });
    }
  }
  if (!res.ok) throw new Error("Failed to fetch rankings");
  return res.json();
}

export function useRanking() {
  return useQuery<{ rankings: RankingEntry[] }>({
    queryKey: ["/api/rankings/around"],
    queryFn: fetchRankings,
  });
}
