// Compatibility launcher for older deployment notes.
// The production cloud API is implemented in cloud-api-server.py to avoid
// native npm dependencies on the small ECS server.

const { spawn } = require("child_process");
const path = require("path");

const script = path.join(__dirname, "cloud-api-server.py");
const candidates = process.platform === "win32" ? ["python", "py"] : ["python3", "python"];

function run(index) {
    if (index >= candidates.length) {
        console.error("Python 3 is required to run eat record cloud API.");
        process.exit(1);
    }
    const child = spawn(candidates[index], [script], { stdio: "inherit" });
    child.on("error", () => run(index + 1));
    child.on("exit", (code, signal) => {
        if (code === 9009 || code === 127) {
            run(index + 1);
            return;
        }
        if (signal) {
            process.kill(process.pid, signal);
            return;
        }
        process.exit(code || 0);
    });
}

run(0);
