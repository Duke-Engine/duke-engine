// A static server for a built page: `node serve.mjs [folder] [port]`, by default build/site on 8090.
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";

const root = process.argv[2] ?? new URL("build/site", import.meta.url).pathname.replace(/^\/(\w:)/, "$1");
const port = Number(process.argv[3] ?? 8090);
const types = { ".html": "text/html", ".js": "text/javascript", ".wasm": "application/wasm", ".png": "image/png",
    ".jpg": "image/jpeg", ".json": "application/json" };

createServer(async (req, res) => {
    const url = decodeURIComponent(new URL(req.url, "http://x").pathname);
    const path = normalize(url.endsWith("/") ? url + "index.html" : url);
    if (path.includes("..")) {
        res.writeHead(400).end();
        return;
    }
    try {
        const body = await readFile(join(root, path));
        res.writeHead(200, { "Content-Type": types[extname(path)] ?? "application/octet-stream" }).end(body);
    } catch {
        res.writeHead(404).end();
    }
}).listen(port, () => console.log(`serving ${root} on http://localhost:${port}`));
