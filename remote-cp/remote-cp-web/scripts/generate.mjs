import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");
const python = path.join(repoRoot, ".venv", "Scripts", "python.exe");
const script = path.join(repoRoot, "scripts", "generate_file_types.py");

const result = spawnSync(python, [script], {
  cwd: repoRoot,
  stdio: "inherit",
  shell: false,
});

process.exit(result.status ?? 1);
