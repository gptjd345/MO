import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { authHeaders, tryRefresh } from "@/lib/queryClient";

export interface WeeklyGoalData {
  goal: number;
  hasGoal: boolean;
  recommended: number;
  year: number;
  weekNumber: number;
}

async function fetchWithRefresh(url: string, init: RequestInit = {}): Promise<Response> {
  let res = await fetch(url, { ...init, headers: authHeaders(init.headers as Record<string, string>) });
  if (res.status === 401) {
    const refreshed = await tryRefresh();
    if (refreshed) res = await fetch(url, { ...init, headers: authHeaders(init.headers as Record<string, string>) });
  }
  return res;
}

export function useWeeklyGoal(year?: number, weekNumber?: number) {
  const params = new URLSearchParams();
  if (year) params.set("year", year.toString());
  if (weekNumber) params.set("weekNumber", weekNumber.toString());

  return useQuery<WeeklyGoalData>({
    queryKey: ["goals", "weekly", year, weekNumber],
    queryFn: async () => {
      const res = await fetchWithRefresh(`/api/goals/weekly?${params}`);
      if (!res.ok) throw new Error("Failed to fetch weekly goal");
      return res.json();
    },
  });
}

export function useSetWeeklyGoal() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: { year: number; weekNumber: number; goalCount: number }) => {
      const res = await fetchWithRefresh("/api/goals/weekly", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || "Failed to set goal");
      }
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["goals", "weekly"] });
    },
  });
}
