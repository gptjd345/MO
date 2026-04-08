import { useQuery } from "@tanstack/react-query";
import { authHeaders, tryRefresh } from "@/lib/queryClient";

export interface CalendarDay {
  date: string;
  completedCount: number;
}

export interface StreakData {
  currentStreak: number;
  longestStreak: number;
  isFreezed: boolean;
  lastWeekAchieved: number | null;
  lastYearAchieved: number | null;
  currentWeekCompleted: number;
  currentWeekGoal: number;
}

export interface WeeklyStatData {
  year: number;
  weekNumber: number;
  completedCount: number;
  scheduledCount: number;
  completionRate: number;
  goalAchieved: boolean;
}

async function fetchWithRefresh(url: string): Promise<Response> {
  let res = await fetch(url, { headers: authHeaders() });
  if (res.status === 401) {
    const refreshed = await tryRefresh();
    if (refreshed) res = await fetch(url, { headers: authHeaders() });
  }
  return res;
}

export function useYearlyStats(year: number) {
  return useQuery<CalendarDay[]>({
    queryKey: ["stats", "calendar", "year", year],
    queryFn: async () => {
      const res = await fetchWithRefresh(`/api/stats/yearly?year=${year}`);
      if (!res.ok) throw new Error("Failed to fetch yearly stats");
      return res.json();
    },
  });
}

export function useCalendarStats(year: number, month: number) {
  return useQuery<CalendarDay[]>({
    queryKey: ["stats", "calendar", year, month],
    queryFn: async () => {
      const res = await fetchWithRefresh(`/api/stats/calendar?year=${year}&month=${month}`);
      if (!res.ok) throw new Error("Failed to fetch calendar stats");
      return res.json();
    },
  });
}

export function useStreak() {
  return useQuery<StreakData>({
    queryKey: ["stats", "streak"],
    queryFn: async () => {
      const res = await fetchWithRefresh("/api/stats/streak");
      if (!res.ok) throw new Error("Failed to fetch streak");
      return res.json();
    },
  });
}

export function useDailyStats(days = 6) {
  return useQuery<CalendarDay[]>({
    queryKey: ["stats", "daily", days],
    queryFn: async () => {
      const res = await fetchWithRefresh(`/api/stats/daily?days=${days}`);
      if (!res.ok) throw new Error("Failed to fetch daily stats");
      return res.json();
    },
  });
}

export function useWeeklyStats(weeks = 8) {
  return useQuery<WeeklyStatData[]>({
    queryKey: ["stats", "weekly", weeks],
    queryFn: async () => {
      const res = await fetchWithRefresh(`/api/stats/weekly?weeks=${weeks}`);
      if (!res.ok) throw new Error("Failed to fetch weekly stats");
      return res.json();
    },
  });
}
