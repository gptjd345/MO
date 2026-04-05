import { QueryClient, QueryFunction } from "@tanstack/react-query";

// access token은 localStorage에 보관
// XSS 방어는 CSP(Content-Security-Policy) 헤더로 처리 (frontend/server/index.ts)
export function getToken(): string | null {
  return localStorage.getItem("access_token");
}

export function setToken(token: string) {
  localStorage.setItem("access_token", token);
}

export function removeToken() {
  localStorage.removeItem("access_token");
}

export function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { ...extra };
  const token = getToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return headers;
}

async function throwIfResNotOk(res: Response) {
  if (!res.ok) {
    const text = (await res.text()) || res.statusText;
    throw new Error(`${res.status}: ${text}`);
  }
}

// refresh token 쿠키로 새 access token 발급 시도
export async function tryRefresh(): Promise<boolean> {
  const res = await fetch("/api/auth/refresh", { method: "POST" });
  if (res.ok) {
    const data = await res.json();
    setToken(data.accessToken);
    return true;
  }
  removeToken();
  localStorage.removeItem("user_data");
  window.dispatchEvent(new Event("auth:logout"));
  return false;
}

export async function apiRequest(
  method: string,
  url: string,
  data?: unknown | undefined,
): Promise<Response> {
  const headers = authHeaders(data ? { "Content-Type": "application/json" } : {});
  let res = await fetch(url, {
    method,
    headers,
    body: data ? JSON.stringify(data) : undefined,
  });

  if (res.status === 401) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      res = await fetch(url, {
        method,
        headers: authHeaders(data ? { "Content-Type": "application/json" } : {}),
        body: data ? JSON.stringify(data) : undefined,
      });
    }
  }

  await throwIfResNotOk(res);
  return res;
}

type UnauthorizedBehavior = "returnNull" | "throw";
export const getQueryFn: <T>(options: {
  on401: UnauthorizedBehavior;
}) => QueryFunction<T> =
  ({ on401: unauthorizedBehavior }) =>
  async ({ queryKey }) => {
    let res = await fetch(queryKey.join("/") as string, {
      headers: authHeaders(),
    });

    if (res.status === 401) {
      const refreshed = await tryRefresh();
      if (refreshed) {
        res = await fetch(queryKey.join("/") as string, { headers: authHeaders() });
      }
    }

    if (unauthorizedBehavior === "returnNull" && res.status === 401) {
      return null;
    }

    await throwIfResNotOk(res);
    return await res.json();
  };

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      queryFn: getQueryFn({ on401: "throw" }),
      refetchInterval: false,
      refetchOnWindowFocus: false,
      staleTime: Infinity,
      retry: false,
    },
    mutations: {
      retry: false,
    },
  },
});
