// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

// Tiny zero-dependency static server for the browser smoke test. Serves the repo root so that
// /ts/dist (the built module) and /vectors resolve, then prints the URL to open.
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../", import.meta.url));
const TYPES = {
  ".html": "text/html",
  ".js": "text/javascript",
  ".mjs": "text/javascript",
  ".json": "application/json",
  ".css": "text/css"
};

createServer(async (req, res) => {
  const reqPath = decodeURIComponent(new URL(req.url, "http://localhost").pathname);
  const file = normalize(join(root, reqPath));
  if (!file.startsWith(root)) {
    res.writeHead(403).end();
    return;
  }
  try {
    const body = await readFile(file);
    res.writeHead(200, { "content-type": TYPES[extname(file)] ?? "application/octet-stream" });
    res.end(body);
  } catch {
    res.writeHead(404).end("not found");
  }
}).listen(8000, () => {
  console.log("Serving. Open  http://localhost:8000/ts/test/browser.html");
});
