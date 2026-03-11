import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { useAuth as useAuthContext } from "@/contexts/AuthContext";

export function useAuth() {
  const { user, isLoading, login, register, logout } = useAuthContext();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const loginMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      login(email, password),
    onSuccess: () => {
      toast({ title: "Welcome back!" });
    },
    onError: (error: Error) => {
      toast({ title: "Login failed", description: error.message, variant: "destructive" });
    },
  });

  const registerMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      register(email, password),
    onSuccess: () => {
      toast({ title: "Account created!", description: "Welcome aboard." });
    },
    onError: (error: Error) => {
      toast({ title: "Registration failed", description: error.message, variant: "destructive" });
    },
  });

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.clear();
      toast({ title: "Logged out", description: "See you next time." });
    },
  });

  return { user, isLoading, loginMutation, registerMutation, logoutMutation };
}
