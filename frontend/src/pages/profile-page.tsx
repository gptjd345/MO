import { useState } from "react";
import { useAuth } from "@/hooks/use-auth";
import { LayoutShell } from "@/components/layout-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { useToast } from "@/hooks/use-toast";
import { apiRequest, removeToken } from "@/lib/queryClient";
import { useQueryClient } from "@tanstack/react-query";
import { User, Mail, Star, Lock, Eye, EyeOff } from "lucide-react";

export default function ProfilePage() {
  const { user, logoutMutation } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();

    if (newPassword !== confirmPassword) {
      toast({ title: "비밀번호 불일치", description: "새 비밀번호와 확인 비밀번호가 다릅니다.", variant: "destructive" });
      return;
    }

    if (newPassword.length < 8) {
      toast({ title: "비밀번호 조건 미충족", description: "새 비밀번호는 8자 이상이어야 합니다.", variant: "destructive" });
      return;
    }

    setIsSubmitting(true);
    try {
      await apiRequest("PATCH", "/api/auth/password", { currentPassword, newPassword });
      toast({ title: "비밀번호 변경 완료", description: "보안을 위해 다시 로그인해주세요." });
      queryClient.clear();
      removeToken();
      localStorage.removeItem("user_data");
      window.dispatchEvent(new Event("auth:logout"));
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "";
      if (message.includes("401")) {
        toast({ title: "현재 비밀번호 오류", description: "현재 비밀번호가 올바르지 않습니다.", variant: "destructive" });
      } else {
        toast({ title: "변경 실패", description: "잠시 후 다시 시도해주세요.", variant: "destructive" });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <LayoutShell>
      <div className="max-w-xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-white">계정 설정</h1>
          <p className="text-sm text-muted-foreground mt-1">회원 정보를 확인하고 비밀번호를 변경할 수 있습니다.</p>
        </div>

        {/* 회원 정보 */}
        <Card className="bg-card border-white/10">
          <CardHeader>
            <CardTitle className="text-white text-base flex items-center gap-2">
              <User className="h-4 w-4" />
              회원 정보
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-3">
              <div className="h-12 w-12 rounded-full bg-gradient-to-br from-primary/40 to-purple-600/40 border border-white/10 flex items-center justify-center text-lg font-bold text-white flex-shrink-0">
                {(user?.nickname ?? user?.email)?.charAt(0).toUpperCase()}
              </div>
              <div>
                <p className="text-white font-semibold">{user?.nickname}</p>
                <p className="text-sm text-muted-foreground">{user?.email}</p>
              </div>
            </div>
            <Separator className="bg-white/5" />
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div className="flex items-center gap-2 text-muted-foreground">
                <Mail className="h-4 w-4" />
                <span>이메일</span>
              </div>
              <span className="text-white">{user?.email}</span>

              <div className="flex items-center gap-2 text-muted-foreground">
                <Star className="h-4 w-4 text-yellow-400" />
                <span>점수</span>
              </div>
              <span className="text-yellow-400 font-medium">{user?.score ?? 0}점</span>
            </div>
          </CardContent>
        </Card>

        {/* 비밀번호 변경 */}
        <Card className="bg-card border-white/10">
          <CardHeader>
            <CardTitle className="text-white text-base flex items-center gap-2">
              <Lock className="h-4 w-4" />
              비밀번호 변경
            </CardTitle>
            <CardDescription>변경 후 모든 디바이스에서 자동으로 로그아웃됩니다.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleChangePassword} className="space-y-4">
              <div className="space-y-2">
                <Label className="text-white/80">현재 비밀번호</Label>
                <div className="relative">
                  <Input
                    type={showCurrent ? "text" : "password"}
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    className="bg-background/50 border-white/10 text-white pr-10"
                    required
                  />
                  <button
                    type="button"
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-white"
                    onClick={() => setShowCurrent(!showCurrent)}
                  >
                    {showCurrent ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              <div className="space-y-2">
                <Label className="text-white/80">새 비밀번호</Label>
                <div className="relative">
                  <Input
                    type={showNew ? "text" : "password"}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    className="bg-background/50 border-white/10 text-white pr-10"
                    required
                    minLength={8}
                  />
                  <button
                    type="button"
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-white"
                    onClick={() => setShowNew(!showNew)}
                  >
                    {showNew ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                <p className="text-xs text-muted-foreground">8자 이상 입력해주세요.</p>
              </div>

              <div className="space-y-2">
                <Label className="text-white/80">새 비밀번호 확인</Label>
                <div className="relative">
                  <Input
                    type={showConfirm ? "text" : "password"}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className="bg-background/50 border-white/10 text-white pr-10"
                    required
                  />
                  <button
                    type="button"
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-white"
                    onClick={() => setShowConfirm(!showConfirm)}
                  >
                    {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              <Button
                type="submit"
                className="w-full"
                disabled={isSubmitting || !currentPassword || !newPassword || !confirmPassword}
              >
                {isSubmitting ? "변경 중..." : "비밀번호 변경"}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </LayoutShell>
  );
}