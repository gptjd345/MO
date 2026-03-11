import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useAuth } from "@/hooks/use-auth";
import { Redirect } from "wouter";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Loader2, CheckCircle2 } from "lucide-react";
import { motion } from "framer-motion";

const loginSchema = z.object({
  email: z.string().email("Please enter a valid email"),
  password: z.string().min(6, "Password must be at least 6 characters"),
});

const registerSchema = z.object({
  email: z.string().email("Please enter a valid email"),
  password: z.string().min(6, "Password must be at least 6 characters"),
});

export default function AuthPage() {
  const { user, loginMutation, registerMutation } = useAuth();
  const [activeTab, setActiveTab] = useState<"login" | "register">("login");

  const loginForm = useForm<z.infer<typeof loginSchema>>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const registerForm = useForm<z.infer<typeof registerSchema>>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: "", password: "" },
  });

  if (user) {
    return <Redirect to="/" />;
  }

  return (
    <div className="min-h-screen grid lg:grid-cols-2 bg-background">
      <div className="flex items-center justify-center p-8">
        <div className="w-full max-w-md space-y-8">
          <div className="space-y-2 text-center lg:text-left">
            <h1 className="text-3xl font-bold font-display tracking-tighter text-white" data-testid="text-welcome">
              Welcome back
            </h1>
            <p className="text-muted-foreground">
              Enter your credentials to access your workspace.
            </p>
          </div>

          <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as any)} className="w-full">
            <TabsList className="grid w-full grid-cols-2 bg-secondary/50 p-1 rounded-xl">
              <TabsTrigger value="login" data-testid="tab-login" className="rounded-lg data-[state=active]:bg-background data-[state=active]:text-white">Login</TabsTrigger>
              <TabsTrigger value="register" data-testid="tab-register" className="rounded-lg data-[state=active]:bg-background data-[state=active]:text-white">Register</TabsTrigger>
            </TabsList>

            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3 }}
              className="mt-6"
            >
              <TabsContent value="login">
                <Card className="border-white/5 bg-card/50 backdrop-blur-sm shadow-xl">
                  <CardHeader>
                    <CardTitle>Login</CardTitle>
                    <CardDescription>Sign in to your account to continue.</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <Form {...loginForm}>
                      <form onSubmit={loginForm.handleSubmit((d) => loginMutation.mutate(d))} className="space-y-4">
                        <FormField
                          control={loginForm.control}
                          name="email"
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>Email</FormLabel>
                              <FormControl>
                                <Input
                                  type="email"
                                  placeholder="you@example.com"
                                  className="bg-background/50 border-white/10 focus:border-primary/50"
                                  data-testid="input-login-email"
                                  {...field}
                                />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                        <FormField
                          control={loginForm.control}
                          name="password"
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>Password</FormLabel>
                              <FormControl>
                                <Input
                                  type="password"
                                  placeholder="Enter your password"
                                  className="bg-background/50 border-white/10 focus:border-primary/50"
                                  data-testid="input-login-password"
                                  {...field}
                                />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                        <Button
                          type="submit"
                          className="w-full"
                          disabled={loginMutation.isPending}
                          data-testid="button-login-submit"
                        >
                          {loginMutation.isPending ? (
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          ) : "Sign In"}
                        </Button>
                      </form>
                    </Form>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="register">
                <Card className="border-white/5 bg-card/50 backdrop-blur-sm shadow-xl">
                  <CardHeader>
                    <CardTitle>Create an account</CardTitle>
                    <CardDescription>Enter your email below to create your account.</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <Form {...registerForm}>
                      <form onSubmit={registerForm.handleSubmit((d) => registerMutation.mutate(d))} className="space-y-4">
                        <FormField
                          control={registerForm.control}
                          name="email"
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>Email</FormLabel>
                              <FormControl>
                                <Input
                                  type="email"
                                  placeholder="you@example.com"
                                  className="bg-background/50 border-white/10 focus:border-primary/50"
                                  data-testid="input-register-email"
                                  {...field}
                                />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                        <FormField
                          control={registerForm.control}
                          name="password"
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>Password</FormLabel>
                              <FormControl>
                                <Input
                                  type="password"
                                  placeholder="Enter your password"
                                  className="bg-background/50 border-white/10 focus:border-primary/50"
                                  data-testid="input-register-password"
                                  {...field}
                                />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                        <Button
                          type="submit"
                          variant="secondary"
                          className="w-full"
                          disabled={registerMutation.isPending}
                          data-testid="button-register-submit"
                        >
                          {registerMutation.isPending ? (
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          ) : "Create Account"}
                        </Button>
                      </form>
                    </Form>
                  </CardContent>
                </Card>
              </TabsContent>
            </motion.div>
          </Tabs>
        </div>
      </div>

      <div className="hidden lg:flex flex-col justify-center items-center bg-card/30 relative overflow-hidden p-12 text-center">
        <div className="absolute top-[-20%] right-[-10%] w-[600px] h-[600px] bg-primary/20 rounded-full blur-[120px] pointer-events-none" />
        <div className="absolute bottom-[-10%] left-[-10%] w-[500px] h-[500px] bg-blue-600/10 rounded-full blur-[100px] pointer-events-none" />
        
        <div className="relative z-10 max-w-lg space-y-6">
          <div className="mx-auto w-24 h-24 bg-gradient-to-br from-primary to-purple-600 rounded-3xl flex items-center justify-center shadow-2xl shadow-primary/30 mb-8 transform -rotate-12 hover:rotate-0 transition-transform duration-500">
            <CheckCircle2 className="h-12 w-12 text-white" />
          </div>
          <h2 className="text-4xl font-bold font-display text-white tracking-tight">
            Manage your tasks with <span className="text-primary glow-text">precision</span>
          </h2>
          <p className="text-lg text-muted-foreground leading-relaxed">
            A beautiful, intuitive dashboard to keep your projects organized and your productivity high.
          </p>
        </div>
      </div>
    </div>
  );
}
