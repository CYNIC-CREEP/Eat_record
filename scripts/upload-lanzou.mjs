import { existsSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const args = new Map();
for (let i = 2; i < process.argv.length; i += 2) {
  args.set(process.argv[i], process.argv[i + 1]);
}

const apk = args.get("--apk");
const code = args.get("--code");
const version = args.get("--version");
const url = args.get("--url");

if (!apk || !existsSync(apk)) {
  throw new Error(`APK file does not exist: ${apk ?? ""}`);
}

if (!code || !existsSync(code)) {
  throw new Error(`Code archive does not exist: ${code ?? ""}`);
}

if (!version) {
  throw new Error("Version is required.");
}

if (!url) {
  throw new Error("Upload URL is required.");
}

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.dirname(__dirname);
const require = createRequire(path.join(projectRoot, ".lanzou-uploader", "package.json"));
const { chromium } = require("playwright");
const userDataDir = path.join(projectRoot, ".lanzou-browser");
const uploadSections = [
  { folder: "安装包", file: apk },
  { folder: "代码", file: code },
];

async function launchContext() {
  const options = {
    headless: false,
    viewport: { width: 1366, height: 860 },
    acceptDownloads: true,
  };

  try {
    return await chromium.launchPersistentContext(userDataDir, {
      ...options,
      channel: process.env.PLAYWRIGHT_CHANNEL || "msedge",
    });
  } catch {
    return chromium.launchPersistentContext(userDataDir, options);
  }
}

async function waitForConsolePage(page) {
  if (page.url().includes("/console/")) return;

  console.log("Please log in to Lanzou in the opened browser window.");
  console.log("After login, open the target folder URL if it does not redirect automatically.");

  await page.waitForURL(/\/console\//, { timeout: 10 * 60 * 1000 });
}

async function findFileInput(page) {
  const direct = page.locator('input[type="file"]').first();
  if (await direct.count()) return direct;

  await clickFirstVisible([
    page.locator("button").filter({ hasText: "上传文件" }).first(),
    page.locator(".uoload-img").filter({ hasText: "上传文件" }).first(),
  ]);
  await waitSettled(page);

  const uploadTriggers = [
    page.locator(".upload-actions-item").filter({ hasText: "上传文件" }).first(),
    page.locator('[title*="上传"]').first(),
    page.locator('[class*="upload" i]').first(),
  ];

  for (const trigger of uploadTriggers) {
    if (!(await trigger.count())) continue;
    try {
      await trigger.first().click({ timeout: 5000 });
      const input = page.locator('input[type="file"]').first();
      await input.waitFor({ state: "attached", timeout: 10000 });
      return input;
    } catch {
      // Try the next likely upload control.
    }
  }

  throw new Error("Could not find Lanzou's file upload control.");
}

async function clickIfVisible(page, text) {
  const locator = page.getByText(text, { exact: false }).first();
  if (!(await locator.count())) return false;

  try {
    await locator.click({ timeout: 5000 });
    return true;
  } catch {
    return false;
  }
}

async function clickFirstVisible(locators, timeout = 5000) {
  for (const locator of locators) {
    if (!(await locator.count())) continue;
    try {
      await locator.first().click({ timeout });
      return true;
    } catch {
      // Try the next locator.
    }
  }

  return false;
}

async function clickVisibleText(page, texts, timeout = 5000) {
  for (const text of texts) {
    const locator = page
      .locator("button:visible, .el-button:visible, span:visible, div:visible")
      .filter({ hasText: text })
      .first();
    if (!(await locator.count())) continue;
    try {
      await locator.click({ timeout });
      return true;
    } catch {
      // Try the next visible text target.
    }
  }

  return false;
}

async function fillFirstVisible(locators, value, timeout = 5000) {
  for (const locator of locators) {
    if (!(await locator.count())) continue;
    try {
      await locator.first().fill(value, { timeout });
      return true;
    } catch {
      // Try the next locator.
    }
  }

  return false;
}

async function waitSettled(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(1200);
}

async function waitForAppContent(page) {
  await page.waitForFunction(() => {
    const app = document.querySelector("#app");
    const bodyText = document.body?.innerText || "";
    return bodyText.trim().length > 20 || (app && app.children.length > 0);
  }, { timeout: 60000 });
  await waitSettled(page);
}

async function openFolder(page, name) {
  const folder = page.getByText(name, { exact: true }).first();
  await folder.waitFor({ timeout: 30000 });
  await folder.dblclick().catch(async () => {
    await folder.click();
    await page.keyboard.press("Enter");
  });
  await waitSettled(page);
}

async function ensureFolder(page, name) {
  if (await page.getByText(name, { exact: true }).count()) return;

  await clickFirstVisible([
    page.locator("button").filter({ hasText: "上传文件" }).first(),
    page.locator(".uoload-img").filter({ hasText: "上传文件" }).first(),
  ]);
  await waitSettled(page);

  const openedCreate = await clickFirstVisible([
    page.locator(".upload-actions-item").filter({ hasText: "新建文件夹" }).first(),
    page.getByText("创建文件夹", { exact: false }),
    page.locator('[title*="新建"]').first(),
    page.locator('[title*="文件夹"]').first(),
  ]);

  if (!openedCreate) {
    const candidates = await page.locator("button, a, span, div, i").evaluateAll((nodes) =>
      nodes
        .map((node) => ({
          tag: node.tagName,
          text: (node.textContent || "").trim().replace(/\s+/g, " ").slice(0, 40),
          title: node.getAttribute("title") || "",
          cls: node.getAttribute("class") || "",
          id: node.getAttribute("id") || "",
        }))
        .filter((item) => item.text || item.title || item.cls || item.id)
        .slice(0, 120)
    );
    console.log(JSON.stringify(candidates, null, 2));
    throw new Error(`Could not find create-folder control for ${name}.`);
  }

  await waitSettled(page);

  const dialog = page.locator(".el-dialog:visible, .el-message-box:visible").last();
  const dialogInput = dialog.locator('input[type="text"], input').first();
  const hasDialogInput = await dialogInput.count();

  const filled = await fillFirstVisible([
    dialogInput,
    page.locator('input[placeholder*="文件夹"]').first(),
    page.locator('input[placeholder*="名称"]').first(),
    page.locator('input[type="text"]').last(),
    page.locator('input').last(),
  ], name);

  if (!filled) {
    throw new Error(`Could not find folder-name input for ${name}.`);
  }

  if (hasDialogInput) {
    await dialogInput.press("Enter").catch(() => {});
  }

  const confirmed = await clickFirstVisible([
    dialog.locator("button.common-btn:visible").first(),
    dialog.locator("button.el-button--primary:visible").first(),
    dialog.locator("button:visible").filter({ hasText: "确定" }).first(),
    dialog.locator("button:visible").filter({ hasText: "确认" }).first(),
    dialog.locator("button:visible").filter({ hasText: "创建" }).first(),
    dialog.locator("button:visible").filter({ hasText: "保存" }).first(),
  ], 3000);

  if (!confirmed) {
    await clickVisibleText(page, ["确定", "确认", "创建", "保存"], 3000);
  }

  await page.getByText(name, { exact: true }).waitFor({ timeout: 30000 });
}

async function uploadFile(page, file) {
  const fileName = path.basename(file);
  const input = await findFileInput(page);
  await input.setInputFiles(file);

  await clickIfVisible(page, "开始上传");
  await clickIfVisible(page, "确定");
  await clickIfVisible(page, "上传");

  await page.getByText(fileName, { exact: false }).waitFor({ timeout: 5 * 60 * 1000 });
  await page.waitForTimeout(3000);
  console.log(`Uploaded ${fileName}.`);
}

const context = await launchContext();
const page = context.pages()[0] || await context.newPage();

try {
  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 60000 });
  await waitForAppContent(page);
  await waitForConsolePage(page);
  if (page.url() !== url) {
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 60000 });
    await waitForAppContent(page);
  }

  await ensureFolder(page, version);
  await openFolder(page, version);
  const versionUrl = page.url();

  for (const section of uploadSections) {
    await ensureFolder(page, section.folder);
    await openFolder(page, section.folder);
    await uploadFile(page, section.file);
    await page.goto(versionUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await waitSettled(page);
  }

  console.log(`Uploaded version ${version} to Lanzou.`);
} catch (error) {
  const screenshot = path.join(projectRoot, "dist", "lanzou-upload-error.png");
  await page.screenshot({ path: screenshot, fullPage: true }).catch(() => {});
  console.error(`Saved upload error screenshot to ${screenshot}`);
  throw error;
} finally {
  await context.close();
}
