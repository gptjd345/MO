import type { Express, Request, Response, NextFunction } from "express";
import { type Server } from "http";
import { spawn, execSync } from "child_process";
import path from "path";
import http from "http";
import { createProxyMiddleware, fixRequestBody } from "http-proxy-middleware";

// SPRING_BOOT_URL이 설정되면 외부 Spring Boot 서비스를 사용 (Docker dev 환경)
// 설정 안 되면 기존처럼 JAR를 직접 spawn
const SPRING_BOOT_URL = process.env.SPRING_BOOT_URL || "http://127.0.0.1:8080";

let springBootReady = false;
let springBootReadyResolve: () => void;
const springBootReadyPromise = new Promise<void>((resolve) => {
  springBootReadyResolve = resolve;
});

function waitForSpringBoot(): void {
  const parsed = new URL(SPRING_BOOT_URL);
  const hostname = parsed.hostname;
  const port = parseInt(parsed.port || "8080", 10);

  const startTime = Date.now();
  let checkCount = 0;
  const check = () => {
    checkCount++;
    const elapsed = Math.round((Date.now() - startTime) / 1000);
    if (checkCount % 10 === 0) {
      console.log(`[startup] Still waiting for Spring Boot at ${SPRING_BOOT_URL}... (${elapsed}s elapsed)`);
    }
    const req = http.request(
      { hostname, port, path: "/api/auth/me", method: "GET", timeout: 3000 },
      (res) => {
        res.resume();
        springBootReady = true;
        console.log(`[startup] Spring Boot is ready at ${SPRING_BOOT_URL} (took ${elapsed}s)`);
        springBootReadyResolve();
      }
    );
    req.on("error", () => {
      setTimeout(check, 2000);
    });
    req.on("timeout", () => {
      req.destroy();
      setTimeout(check, 2000);
    });
    req.end();
  };
  check();
}

function ensureRedisRunning(): void {
  const redisHost = process.env.REDIS_HOST || "127.0.0.1";
  const redisPort = process.env.REDIS_PORT || "6379";

  if (redisHost !== "127.0.0.1" && redisHost !== "localhost") {
    console.log(`[startup] Redis expected at external host ${redisHost}:${redisPort}, skipping local start`);
    return;
  }

  for (let i = 0; i < 5; i++) {
    try {
      execSync(`redis-cli -h ${redisHost} -p ${redisPort} ping`, { stdio: "pipe", timeout: 2000 });
      console.log("[startup] Redis is running");
      return;
    } catch {
      console.log(`[startup] Starting Redis (attempt ${i + 1})...`);
      try {
        execSync(`redis-server --daemonize yes --port ${redisPort} --bind 127.0.0.1`, { stdio: "pipe", timeout: 5000 });
      } catch (e) {
        console.error("[startup] redis-server command failed, retrying...");
      }
      try {
        execSync("sleep 1", { stdio: "ignore" });
        execSync(`redis-cli -h ${redisHost} -p ${redisPort} ping`, { stdio: "pipe", timeout: 2000 });
        console.log("[startup] Redis started successfully");
        return;
      } catch {
        console.log("[startup] Redis not ready yet...");
      }
    }
  }
  console.error("[startup] WARNING: Redis may not be running. Spring Boot may fail to connect.");
}

function startBackendServices() {
  const workspaceDir = path.resolve(process.cwd());

  // SPRING_BOOT_URL이 외부 호스트를 가리키면 JAR spawn 없이 준비 대기만
  const isExternal = !SPRING_BOOT_URL.includes("127.0.0.1") && !SPRING_BOOT_URL.includes("localhost");
  if (isExternal) {
    console.log(`[startup] Using external Spring Boot at ${SPRING_BOOT_URL}`);
    waitForSpringBoot();
    return;
  }

  ensureRedisRunning();

  const jarPath = path.join(workspaceDir, "backend", "target", "todo-app-1.0.0.jar");
  console.log("[startup] Starting Spring Boot from:", jarPath);

  const springBoot = spawn("java", ["-jar", jarPath], {
    cwd: path.join(workspaceDir, "backend"),
    stdio: ["ignore", "pipe", "pipe"],
    env: { ...process.env },
  });

  springBoot.stdout.on("data", (data: Buffer) => {
    const line = data.toString().trim();
    if (line) console.log("[spring-boot]", line);
  });

  springBoot.stderr.on("data", (data: Buffer) => {
    const line = data.toString().trim();
    if (line) console.error("[spring-boot]", line);
  });

  springBoot.on("error", (err) => {
    console.error("[startup] Failed to start Spring Boot:", err);
  });

  springBoot.on("exit", (code) => {
    console.error("[startup] Spring Boot exited with code:", code);
    springBootReady = false;
  });

  waitForSpringBoot();
}

export async function registerRoutes(
  httpServer: Server,
  app: Express
): Promise<Server> {
  startBackendServices();

  const apiProxy = createProxyMiddleware({
    target: SPRING_BOOT_URL,
    changeOrigin: true,
    on: {
      error: (err, _req, res) => {
        console.error("[proxy] Error:", err.message);
        if (res && "writeHead" in res && !res.headersSent) {
          (res as any).writeHead(503, { "Content-Type": "application/json" });
          (res as any).end(JSON.stringify({ message: "Backend service is unavailable, please try again." }));
        }
      },
      proxyReq: (proxyReq, req) => {
        proxyReq.path = (req as Request).originalUrl;
        fixRequestBody(proxyReq, req);
      },
    },
  });

  app.use("/api", async (req: Request, res: Response, next: NextFunction) => {
    if (!springBootReady) {
      console.log("[proxy] Request waiting for Spring Boot...");
      await springBootReadyPromise;
    }
    apiProxy(req, res, next);
  });

  return httpServer;
}
