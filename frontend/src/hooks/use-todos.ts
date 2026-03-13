import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { getToken, tryRefresh } from "@/lib/queryClient";
import { useAuth } from "@/contexts/AuthContext";

export interface TodoUpdateResponse {
  todo: Todo;
  pointsEarned: number;
}

export interface Todo {
  id: number;
  title: string;
  content: string | null;
  completed: boolean;
  startDate: string | null;
  endDate: string | null;
  priority: "HIGH" | "MEDIUM" | "LOW";
  userId: number;
}

function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { ...extra };
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
}

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

export function useTodos() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { addScore } = useAuth();

  const todosQuery = useQuery<Todo[]>({
    queryKey: ["/api/todos"],
    queryFn: async () => {
      const res = await fetchWithRefresh("/api/todos");
      if (!res.ok) throw new Error("Failed to fetch todos");
      return await res.json();
    },
  });

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
      queryClient.invalidateQueries({ queryKey: ["/api/todos"] });
      toast({ title: "Task added", description: "Let's get this done." });
    },
    onError: (error: Error) => {
      toast({ title: "Error", description: error.message, variant: "destructive" });
    },
  });

  const updateTodoMutation = useMutation({
    mutationFn: async ({ id, ...data }: { id: number; title?: string; content?: string; completed?: boolean; startDate?: string; endDate?: string; priority?: string }): Promise<TodoUpdateResponse> => {
      const res = await fetchWithRefresh(`/api/todos/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      if (!res.ok) throw new Error("Failed to update todo");
      return await res.json();
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["/api/todos"] });
      if (data.pointsEarned > 0) {
        addScore(data.pointsEarned);
        toast({
          title: `+${data.pointsEarned}점 획득!`,
          description: data.pointsEarned === 10 ? "기한 내 완료!" : "완료! (기한 초과)",
        });
      }
    },
    onError: (error: Error) => {
      toast({ title: "Update failed", description: error.message, variant: "destructive" });
    },
  });

  const deleteTodoMutation = useMutation({
    mutationFn: async (id: number) => {
      const res = await fetchWithRefresh(`/api/todos/${id}`, { method: "DELETE" });
      if (!res.ok) throw new Error("Failed to delete todo");
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["/api/todos"] });
      toast({ title: "Task deleted", description: "Cleared from your list." });
    },
    onError: (error: Error) => {
      toast({ title: "Delete failed", description: error.message, variant: "destructive" });
    },
  });

  return {
    todos: todosQuery.data ?? [],
    isLoading: todosQuery.isLoading,
    isError: todosQuery.isError,
    createTodo: createTodoMutation.mutate,
    isCreating: createTodoMutation.isPending,
    updateTodo: updateTodoMutation.mutate,
    isUpdating: updateTodoMutation.isPending,
    deleteTodo: deleteTodoMutation.mutate,
  };
}
