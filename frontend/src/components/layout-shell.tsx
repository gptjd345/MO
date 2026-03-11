import { ReactNode, useState } from "react";
import { useAuth } from "@/hooks/use-auth";
import { useTodos } from "@/hooks/use-todos";
import { Link } from "wouter";
import { LogOut, LayoutDashboard, CheckCircle2, Crown, Zap, Check, X, Loader2, AlertTriangle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Progress } from "@/components/ui/progress";
import { useToast } from "@/hooks/use-toast";
import { getToken } from "@/lib/queryClient";
import { useQueryClient } from "@tanstack/react-query";

export const FREE_TASK_LIMIT = 10;

interface LayoutShellProps {
  children: ReactNode;
}

interface PaymentResult {
  id: number;
  status: string;
  pgTransactionId: string | null;
  failReason: string | null;
  amount: number;
}

type PaymentStep = "plan" | "processing" | "success" | "fail";

function failReasonLabel(reason: string | null): string {
  switch (reason) {
    case "INSUFFICIENT_FUNDS": return "잔액이 부족합니다. 다른 결제 수단을 이용해주세요.";
    case "DUPLICATE_REQUEST": return "중복된 결제 요청입니다. 잠시 후 다시 시도해주세요.";
    case "SYSTEM_ERROR_AFTER_RETRIES": return "결제 시스템에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";
    default: return reason || "알 수 없는 오류가 발생했습니다.";
  }
}

function PlanDialog({ open, onOpenChange, taskCount, isPro }: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  taskCount: number;
  isPro: boolean;
}) {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [step, setStep] = useState<PaymentStep>("plan");
  const [paymentResult, setPaymentResult] = useState<PaymentResult | null>(null);

  const usagePercent = Math.min((taskCount / FREE_TASK_LIMIT) * 100, 100);

  const handleClose = (val: boolean) => {
    if (step === "processing") return;
    if (!val) {
      setStep("plan");
      setPaymentResult(null);
    }
    onOpenChange(val);
  };

  const handlePayment = async () => {
    setStep("processing");
    const idempotencyKey = `pro_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;

    try {
      const token = getToken();
      const res = await fetch("/api/payments/subscribe", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ idempotencyKey }),
      });

      if (!res.ok) {
        const err = await res.json();
        setPaymentResult({ id: 0, status: "FAIL", pgTransactionId: null, failReason: err.message || "REQUEST_ERROR", amount: 9900 });
        setStep("fail");
        return;
      }

      const result: PaymentResult = await res.json();
      setPaymentResult(result);

      if (result.status === "SUCCESS") {
        setStep("success");
        queryClient.invalidateQueries({ queryKey: ["/api/auth/me"] });
        toast({ title: "결제 완료!", description: "Pro 플랜이 활성화되었습니다." });
      } else {
        setStep("fail");
      }
    } catch {
      setPaymentResult({ id: 0, status: "FAIL", pgTransactionId: null, failReason: "NETWORK_ERROR", amount: 9900 });
      setStep("fail");
    }
  };

  const handleRetry = () => {
    setPaymentResult(null);
    handlePayment();
  };

  if (isPro && step === "plan") {
    return (
      <Dialog open={open} onOpenChange={handleClose}>
        <DialogContent className="bg-card border-white/10 sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold text-white">플랜 안내</DialogTitle>
            <DialogDescription>현재 Pro 플랜을 사용 중입니다.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col items-center py-6 gap-4">
            <div className="h-16 w-16 rounded-full bg-yellow-500/10 flex items-center justify-center">
              <Crown className="h-8 w-8 text-yellow-400" />
            </div>
            <h3 className="text-lg font-bold text-white">Pro 플랜 활성 중</h3>
            <p className="text-sm text-muted-foreground text-center">무제한 일정과 모든 프리미엄 기능을 이용하실 수 있습니다.</p>
            <ul className="space-y-2 w-full max-w-xs">
              <PlanFeature included text="무제한 일정" highlight />
              <PlanFeature included text="우선순위 설정" />
              <PlanFeature included text="날짜 범위 설정" />
              <PlanFeature included text="검색 및 정렬" />
              <PlanFeature included text="팀 공유 기능" />
              <PlanFeature included text="우선 고객 지원" />
            </ul>
          </div>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="bg-card border-white/10 sm:max-w-lg">
        {step === "plan" && (
          <>
            <DialogHeader>
              <DialogTitle className="text-xl font-bold text-white">플랜 안내</DialogTitle>
              <DialogDescription>현재 무료 플랜을 사용 중입니다.</DialogDescription>
            </DialogHeader>

            <div className="mt-2 mb-6 p-4 rounded-xl bg-background/50 border border-white/10">
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm text-muted-foreground">일정 사용량</span>
                <span className="text-sm font-medium text-white" data-testid="text-usage-count">{taskCount} / {FREE_TASK_LIMIT}</span>
              </div>
              <Progress value={usagePercent} className="h-2" data-testid="progress-task-usage" />
              {taskCount >= FREE_TASK_LIMIT && (
                <p className="text-xs text-red-400 mt-2" data-testid="text-limit-reached">일정 개수가 최대치에 도달했습니다. 유료 플랜으로 업그레이드하세요.</p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="p-5 rounded-xl border-2 border-primary/50 bg-primary/5 relative" data-testid="card-free-plan">
                <div className="absolute -top-2.5 left-4">
                  <Badge className="bg-primary text-white text-xs">현재 플랜</Badge>
                </div>
                <div className="flex items-center gap-2 mb-4 mt-1">
                  <Zap className="h-5 w-5 text-primary" />
                  <h3 className="text-lg font-bold text-white">Free</h3>
                </div>
                <p className="text-2xl font-bold text-white mb-1" data-testid="text-free-price">₩0<span className="text-sm font-normal text-muted-foreground">/월</span></p>
                <p className="text-xs text-muted-foreground mb-4">기본 기능 무료 제공</p>
                <ul className="space-y-2.5">
                  <PlanFeature included text="일정 최대 10개" />
                  <PlanFeature included text="우선순위 설정" />
                  <PlanFeature included text="날짜 범위 설정" />
                  <PlanFeature included text="검색 및 정렬" />
                  <PlanFeature included={false} text="무제한 일정" />
                  <PlanFeature included={false} text="팀 공유 기능" />
                  <PlanFeature included={false} text="우선 고객 지원" />
                </ul>
              </div>

              <div className="p-5 rounded-xl border border-white/10 bg-card relative" data-testid="card-pro-plan">
                <div className="absolute -top-2.5 left-4">
                  <Badge variant="outline" className="border-yellow-500/50 text-yellow-400 text-xs bg-yellow-500/10">추천</Badge>
                </div>
                <div className="flex items-center gap-2 mb-4 mt-1">
                  <Crown className="h-5 w-5 text-yellow-400" />
                  <h3 className="text-lg font-bold text-white">Pro</h3>
                </div>
                <p className="text-2xl font-bold text-white mb-1" data-testid="text-pro-price">₩9,900<span className="text-sm font-normal text-muted-foreground">/월</span></p>
                <p className="text-xs text-muted-foreground mb-4">모든 기능을 제한 없이</p>
                <ul className="space-y-2.5">
                  <PlanFeature included text="무제한 일정" highlight />
                  <PlanFeature included text="우선순위 설정" />
                  <PlanFeature included text="날짜 범위 설정" />
                  <PlanFeature included text="검색 및 정렬" />
                  <PlanFeature included text="팀 공유 기능" />
                  <PlanFeature included text="우선 고객 지원" />
                  <PlanFeature included text="고급 통계" />
                </ul>
              </div>
            </div>

            <Button
              className="w-full mt-4 bg-gradient-to-r from-yellow-500 to-amber-600 text-white font-semibold"
              data-testid="button-upgrade-plan"
              onClick={handlePayment}
            >
              <Crown className="mr-2 h-4 w-4" />
              Pro 플랜으로 업그레이드 (₩9,900)
            </Button>
          </>
        )}

        {step === "processing" && (
          <>
            <DialogHeader>
              <DialogTitle className="text-xl font-bold text-white">결제 처리 중</DialogTitle>
              <DialogDescription>잠시만 기다려주세요.</DialogDescription>
            </DialogHeader>
            <div className="flex flex-col items-center py-12 gap-4" data-testid="payment-processing">
              <Loader2 className="h-12 w-12 text-primary animate-spin" />
              <p className="text-white font-medium">결제를 처리하고 있습니다...</p>
              <p className="text-sm text-muted-foreground">PG사 승인을 기다리는 중입니다</p>
            </div>
          </>
        )}

        {step === "success" && (
          <>
            <DialogHeader>
              <DialogTitle className="text-xl font-bold text-white">결제 완료</DialogTitle>
              <DialogDescription>Pro 플랜이 활성화되었습니다.</DialogDescription>
            </DialogHeader>
            <div className="flex flex-col items-center py-8 gap-4" data-testid="payment-success">
              <div className="h-16 w-16 rounded-full bg-green-500/10 flex items-center justify-center">
                <Check className="h-8 w-8 text-green-400" />
              </div>
              <h3 className="text-lg font-bold text-white">결제가 완료되었습니다!</h3>
              <div className="text-sm text-muted-foreground text-center space-y-1">
                <p>결제 금액: <span className="text-white font-medium">₩9,900</span></p>
                {paymentResult?.pgTransactionId && (
                  <p data-testid="text-transaction-id">거래 번호: <span className="text-white font-mono text-xs">{paymentResult.pgTransactionId.slice(0, 12)}...</span></p>
                )}
              </div>
              <div className="mt-2 p-4 rounded-xl bg-yellow-500/5 border border-yellow-500/20 w-full">
                <div className="flex items-center gap-2 mb-2">
                  <Crown className="h-5 w-5 text-yellow-400" />
                  <span className="text-white font-semibold">Pro 플랜 활성화</span>
                </div>
                <p className="text-xs text-muted-foreground">이제 무제한으로 일정을 관리할 수 있습니다.</p>
              </div>
              <Button
                className="w-full mt-2"
                onClick={() => handleClose(false)}
                data-testid="button-payment-done"
              >
                확인
              </Button>
            </div>
          </>
        )}

        {step === "fail" && (
          <>
            <DialogHeader>
              <DialogTitle className="text-xl font-bold text-white">결제 실패</DialogTitle>
              <DialogDescription>결제 처리 중 문제가 발생했습니다.</DialogDescription>
            </DialogHeader>
            <div className="flex flex-col items-center py-8 gap-4" data-testid="payment-fail">
              <div className="h-16 w-16 rounded-full bg-red-500/10 flex items-center justify-center">
                <AlertTriangle className="h-8 w-8 text-red-400" />
              </div>
              <h3 className="text-lg font-bold text-white">결제에 실패했습니다</h3>
              <p className="text-sm text-muted-foreground text-center" data-testid="text-fail-reason">
                {failReasonLabel(paymentResult?.failReason || null)}
              </p>
              <div className="flex gap-2 w-full mt-2">
                <Button
                  variant="outline"
                  className="flex-1"
                  onClick={() => { setStep("plan"); setPaymentResult(null); }}
                  data-testid="button-payment-back"
                >
                  돌아가기
                </Button>
                <Button
                  className="flex-1"
                  onClick={handleRetry}
                  data-testid="button-payment-retry"
                >
                  <RefreshCw className="mr-2 h-4 w-4" />
                  다시 시도
                </Button>
              </div>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

function PlanFeature({ included, text, highlight }: { included: boolean; text: string; highlight?: boolean }) {
  const testId = `feature-${text.replace(/\s+/g, "-")}`;
  return (
    <li className="flex items-center gap-2 text-sm" data-testid={testId}>
      {included ? (
        <Check className={`h-4 w-4 shrink-0 ${highlight ? "text-yellow-400" : "text-green-400"}`} />
      ) : (
        <X className="h-4 w-4 shrink-0 text-muted-foreground/40" />
      )}
      <span className={included ? (highlight ? "text-yellow-400 font-medium" : "text-white/80") : "text-muted-foreground/50"}>
        {text}
      </span>
    </li>
  );
}

export function LayoutShell({ children }: LayoutShellProps) {
  const { user, logoutMutation } = useAuth();
  const { todos } = useTodos();
  const [planDialogOpen, setPlanDialogOpen] = useState(false);

  const taskCount = todos.length;
  const isPro = user?.plan === "PRO";

  return (
    <div className="min-h-screen bg-background flex flex-col md:flex-row">
      <aside className="w-full md:w-64 bg-card border-b md:border-b-0 md:border-r border-white/5 p-6 flex flex-col sticky top-0 md:h-screen z-10">
        <div className="flex items-center gap-3 mb-10">
          <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-primary to-purple-600 flex items-center justify-center shadow-lg shadow-primary/20">
            <CheckCircle2 className="h-6 w-6 text-white" />
          </div>
          <h1 className="text-xl font-bold font-display tracking-tight text-white">TaskFlow</h1>
        </div>

        <nav className="flex-1 space-y-2">
          <Link href="/">
            <div className="flex items-center gap-3 px-4 py-3 rounded-xl bg-primary/10 text-primary font-medium cursor-pointer transition-colors hover:bg-primary/20">
              <LayoutDashboard className="h-5 w-5" />
              <span>Dashboard</span>
            </div>
          </Link>
        </nav>

        <div className="mt-auto pt-6 border-t border-white/5">
          <div className="flex items-center gap-3 mb-4 px-2">
            <div className="h-8 w-8 rounded-full bg-secondary flex items-center justify-center text-xs font-bold text-secondary-foreground">
              {user?.email?.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-white truncate" data-testid="text-user-email">{user?.email}</p>
            </div>
          </div>

          <Button
            variant="ghost"
            className="w-full justify-start text-muted-foreground mb-1"
            onClick={() => setPlanDialogOpen(true)}
            data-testid="button-plan-indicator"
          >
            {isPro ? (
              <>
                <Crown className="mr-2 h-4 w-4 text-yellow-400" />
                <span>Pro 플랜</span>
                <Badge variant="outline" className="ml-auto text-xs border-yellow-500/30 text-yellow-400 bg-yellow-500/10" data-testid="badge-plan-pro">
                  PRO
                </Badge>
              </>
            ) : (
              <>
                <Zap className="mr-2 h-4 w-4 text-primary" />
                <span>Free 플랜</span>
                <Badge variant="outline" className="ml-auto text-xs border-primary/30 text-primary bg-primary/10" data-testid="badge-task-count">
                  {taskCount}/{FREE_TASK_LIMIT}
                </Badge>
              </>
            )}
          </Button>

          <Button
            variant="ghost"
            className="w-full justify-start text-muted-foreground"
            onClick={() => logoutMutation.mutate()}
            data-testid="button-logout"
          >
            <LogOut className="mr-2 h-4 w-4" />
            Logout
          </Button>
        </div>
      </aside>

      <main className="flex-1 overflow-y-auto">
        <div className="max-w-5xl mx-auto p-4 md:p-8 lg:p-12">
          {children}
        </div>
      </main>

      <PlanDialog open={planDialogOpen} onOpenChange={setPlanDialogOpen} taskCount={taskCount} isPro={isPro} />
    </div>
  );
}
