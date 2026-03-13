import { createContext, useContext, useState, useCallback, ReactNode } from "react";
import { setToken, removeToken, tryRefresh, getToken } from "@/lib/queryClient";

interface User {
  id: number;
  email: string;
  nickname: string;
  plan: string;
  score: number;
}

interface AuthContextValue {
  user: User | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, nickname: string) => Promise<void>;
  logout: () => Promise<void>;
  addScore: (points: number) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function loadUser(): User | null {
  try {
    const raw = localStorage.getItem("user_data");
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function saveUser(user: User) {
  localStorage.setItem("user_data", JSON.stringify(user));
}

function clearUser() {
  localStorage.removeItem("user_data");
  removeToken();
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // 마운트 시 localStorage에서 바로 읽기 — API 호출 없음
  const [user, setUser] = useState<User | null>(loadUser);
  const [isLoading, setIsLoading] = useState(false);

  const login = useCallback(async (email: string, password: string) => {
    setIsLoading(true);
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.message || "Login failed");
      }
      const data = await res.json();
      const userData = { id: data.userId, email: data.email, nickname: data.nickname ?? data.email.split("@")[0], plan: data.plan, score: data.score ?? 0 };
      setToken(data.accessToken);
      saveUser(userData);
      setUser(userData);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const register = useCallback(async (email: string, password: string, nickname: string) => {
    setIsLoading(true);
    try {
      const res = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, nickname }),
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.message || "Registration failed");
      }
      const data = await res.json();
      const userData = { id: data.userId, email: data.email, nickname: data.nickname ?? nickname, plan: data.plan, score: data.score ?? 0 };
      setToken(data.accessToken);
      saveUser(userData);
      setUser(userData);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    await fetch("/api/auth/logout", { method: "POST" });
    clearUser();
    setUser(null);
  }, []);

  const addScore = useCallback((points: number) => {
    setUser(prev => {
      if (!prev) return prev;
      const updated = { ...prev, score: prev.score + points };
      saveUser(updated);
      return updated;
    });
  }, []);

  return (
    <AuthContext.Provider value={{ user, isLoading, login, register, logout, addScore }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}

// access token 만료 시 plan 변경 등 최신 user 정보 동기화
export async function syncUser(setUser: (u: User) => void) {
  const token = getToken();
  if (!token) return;
  const res = await fetch("/api/auth/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status === 401) {
    const refreshed = await tryRefresh();
    if (!refreshed) return;
    const retryRes = await fetch("/api/auth/me", {
      headers: { Authorization: `Bearer ${getToken()}` },
    });
    if (retryRes.ok) {
      const data = await retryRes.json();
      saveUser(data);
      setUser(data);
    }
    return;
  }
  if (res.ok) {
    const data = await res.json();
    saveUser(data);
    setUser(data);
  }
}
