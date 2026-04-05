import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { authHeaders, tryRefresh } from "@/lib/queryClient";
import { useAuth } from "@/contexts/AuthContext";

export interface Todo {
  id: number;
  title: string;
  content: string | null;
  completed: boolean;
  startDate: string | null;
  endDate: string | null;
  priority: "HIGH" | "MEDIUM" | "LOW";
  userId: number;
  completedAt: string | null;
}

export interface PagedTodoResponse {
  content: Todo[];
  totalCount: number;
  totalPages: number;
}

export type SortOption = "newest" | "oldest" | "name" | "deadline" | "priority";

async function fetchWithRefresh(url: string, init: RequestInit = {}): Promise<Response> {
  let res = await fetch(url, { ...init, headers: authHeaders(init.headers as Record<string, string>) });
  if (res.status === 401) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      res = await fetch(url, { ...init, headers: authHeaders(init.headers as Record<string, string>) });
    }
  }
  return res;
}

function buildTodoUrl(completed: boolean, page: number, sort: SortOption, search: string): string {
  const params = new URLSearchParams({
    completed: String(completed),
    page: String(page),
    size: "10",
    sort,
  });
  if (search) params.set("search", search);
  return `/api/todos?${params}`;
}

export function useActiveTodos(page: number, sort: SortOption, search: string) {
  return useQuery<PagedTodoResponse>({
    queryKey: ["todos", "active", page, sort, search],
    queryFn: async () => {
      const res = await fetchWithRefresh(buildTodoUrl(false, page, sort, search));
      if (!res.ok) throw new Error("Failed to fetch active todos");
      return res.json();
    },
  });
}

export function useCompletedTodos(page: number, sort: SortOption, search: string) {
  return useQuery<PagedTodoResponse>({
    queryKey: ["todos", "completed", page, sort, search],
    queryFn: async () => {
      const res = await fetchWithRefresh(buildTodoUrl(true, page, sort, search));
      if (!res.ok) throw new Error("Failed to fetch completed todos");
      return res.json();
    },
  });
}

export function useTodoMutations() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { addScore } = useAuth();

  const invalidateTodos = () => queryClient.invalidateQueries({ queryKey: ["todos"] });

  const createTodoMutation = useMutation({
    mutationFn: async (data: { title: string; content?: string; startDate?: string; endDate?: string; priority?: string }) => {
      const res = await fetchWithRefresh("/api/todos", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      if (!res.ok) throw new Error("Failed to create todo");
      return await res.json();
    },
    onSuccess: () => {
      invalidateTodos();
      toast({ title: "Task added", description: "Let's get this done." });
    },
    onError: (error: Error) => {
      toast({ title: "Error", description: error.message, variant: "destructive" });
    },
  });

  const updateTodoMutation = useMutation({
    mutationFn: async ({ id, ...data }: { id: number; title?: string; content?: string; startDate?: string; endDate?: string; priority?: string }): Promise<Todo> => {
      const res = await fetchWithRefresh(`/api/todos/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      if (!res.ok) throw new Error("Failed to update todo");
      return await res.json();
    },
    onSuccess: () => {
      invalidateTodos();
    },
    onError: (error: Error) => {
      toast({ title: "Update failed", description: error.message, variant: "destructive" });
    },
  });

  const batchUndoMutation = useMutation({
    mutationFn: async (ids: number[]): Promise<{ totalPointsDeducted: number }> => {
      const res = await fetchWithRefresh("/api/todos/batch-undo", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids }),
      });
      if (!res.ok) throw new Error("Failed to undo tasks");
      return await res.json();
    },
    onSuccess: (data) => {
      invalidateTodos();
      setTimeout(() => {
        queryClient.invalidateQueries({ queryKey: ["stats"] });
      }, 800);
      if (data.totalPointsDeducted > 0) {
        addScore(-data.totalPointsDeducted);
        toast({
          title: `-${data.totalPointsDeducted}점 차감`,
          description: "완료 취소로 점수가 차감되었어요.",
        });
      }
    },
    onError: (error: Error) => {
      toast({ title: "Error", description: error.message, variant: "destructive" });
    },
  });

  const batchCompleteMutation = useMutation({
    mutationFn: async (ids: number[]): Promise<{ totalPointsEarned: number }> => {
      const res = await fetchWithRefresh("/api/todos/batch-complete", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids }),
      });
      if (!res.ok) throw new Error("Failed to complete tasks");
      return await res.json();
    },
    onSuccess: (data) => {
      invalidateTodos();
      setTimeout(() => {
        queryClient.invalidateQueries({ queryKey: ["stats"] });
      }, 800);
      if (data.totalPointsEarned > 0) {
        addScore(data.totalPointsEarned);
        toast({
          title: `+${data.totalPointsEarned}점 획득!`,
          description: "선택한 일정을 모두 완료했어요.",
        });
      }
    },
    onError: (error: Error) => {
      toast({ title: "Error", description: error.message, variant: "destructive" });
    },
  });

  const deleteTodoMutation = useMutation({
    mutationFn: async (id: number) => {
      const res = await fetchWithRefresh(`/api/todos/${id}`, { method: "DELETE" });
      if (!res.ok) throw new Error("Failed to delete todo");
    },
    onSuccess: () => {
      invalidateTodos();
      toast({ title: "Task deleted", description: "Cleared from your list." });
    },
    onError: (error: Error) => {
      toast({ title: "Delete failed", description: error.message, variant: "destructive" });
    },
  });

  return {
    createTodo: createTodoMutation.mutate,
    isCreating: createTodoMutation.isPending,
    updateTodo: updateTodoMutation.mutate,
    isUpdating: updateTodoMutation.isPending,
    batchComplete: batchCompleteMutation.mutate,
    batchUndo: batchUndoMutation.mutate,
    deleteTodo: deleteTodoMutation.mutate,
  };
}
