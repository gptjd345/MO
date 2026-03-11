import express, { type Express } from "express";
import fs from "fs";
import path from "path";

export function serveStatic(app: Express) {
  const distPath = path.resolve(__dirname, "public");
  if (!fs.existsSync(distPath)) {
    throw new Error(
      `Could not find the build directory: ${distPath}, make sure to build the client first`,
    );
  }

  app.use(express.static(distPath));

  // index.html 요청 시 nonce를 script 태그에 주입
  app.use("/{*path}", (_req, res) => {
    const nonce = res.locals.nonce as string | undefined;
    const indexPath = path.resolve(distPath, "index.html");

    if (!nonce) {
      return res.sendFile(indexPath);
    }

    const html = fs.readFileSync(indexPath, "utf-8");
    const injected = html.replace(/<script/g, `<script nonce="${nonce}"`);
    res.status(200).set({ "Content-Type": "text/html" }).end(injected);
  });
}
