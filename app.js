"use strict";

const STORE_KEY = "htmlmake_projects_v1";
const ACTIVE_KEY = "htmlmake_active_project";
const API_KEY_STORE = "htmlmake_groq_key";
const MODEL = "openai/gpt-oss-120b";

const $ = (id) => document.getElementById(id);
const state = { projects: [], activeId: null, selectedFile: null, busy: false, previewUrl: null, toastTimer: null };

function starterFiles() {
  return {
    "index.html": `<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>My Website</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <main class="hero">
    <span class="tag">MY FIRST PROJECT</span>
    <h1>Hello, world.</h1>
    <p>Your website starts here. Ask AI to make it yours.</p>
    <button id="helloButton">Click me</button>
  </main>
  <script src="app.js"><\/script>
</body>
</html>`,
    "style.css": `* { box-sizing: border-box; }
body { margin: 0; min-height: 100vh; display: grid; place-items: center; font-family: system-ui, sans-serif; background: #101827; color: #f8fafc; }
.hero { text-align: center; padding: 32px; }
.tag { color: #6ee7b7; letter-spacing: 2px; font-size: 11px; font-weight: 800; }
h1 { font-size: clamp(42px, 8vw, 76px); margin: 16px 0; }
p { color: #aab8c8; }
button { border: 0; border-radius: 10px; padding: 12px 20px; background: #6ee7b7; color: #10231c; font-weight: 800; }`,
    "app.js": `document.getElementById("helloButton")?.addEventListener("click", () => {
  alert("Your website is working!");
});`
  };
}

function load() {
  try { state.projects = JSON.parse(localStorage.getItem(STORE_KEY) || "[]"); } catch { state.projects = []; }
  state.activeId = localStorage.getItem(ACTIVE_KEY);
  if (!state.projects.length) createProject("My first website", false);
  if (!getProject()) state.activeId = state.projects[0].id;
  persist();
}
function persist() {
  localStorage.setItem(STORE_KEY, JSON.stringify(state.projects));
  if (state.activeId) localStorage.setItem(ACTIVE_KEY, state.activeId);
}
function getProject() { return state.projects.find(p => p.id === state.activeId); }
function createProject(name, notify = true) {
  const project = { id: "p_" + Date.now().toString(36) + Math.random().toString(36).slice(2, 7), name: (name || "Untitled project").trim(), files: starterFiles(), messages: [], createdAt: Date.now(), updatedAt: Date.now() };
  state.projects.unshift(project); state.activeId = project.id; state.selectedFile = "index.html"; persist(); renderAll(); if (notify) toast("Project created"); return project;
}
function switchProject(id) { if (!state.projects.some(p => p.id === id)) return; state.activeId = id; state.selectedFile = "index.html"; persist(); renderAll(); navigate("files"); }
function updateProject() { const p = getProject(); if (p) { p.updatedAt = Date.now(); persist(); } }

function toast(message) {
  const el = $("toast"); el.textContent = message; el.classList.add("show"); clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => el.classList.remove("show"), 2200);
}
function navigate(page) {
  document.querySelectorAll(".page").forEach(el => el.classList.toggle("active", el.id === "page-" + page));
  document.querySelectorAll(".nav-item").forEach(el => el.classList.toggle("active", el.dataset.page === page));
  if (page === "files") renderFiles();
  if (page === "preview") renderPreview();
  if (page === "aichat") renderChat();
}
function renderAll() { renderHome(); renderFiles(); renderChat(); $("project-label").textContent = getProject()?.name || "No project open"; }
function renderHome() {
  const grid = $("project-grid"); grid.innerHTML = "";
  $("project-count").textContent = state.projects.length + (state.projects.length === 1 ? " project" : " projects");
  $("empty-projects").classList.toggle("hidden", state.projects.length > 0);
  state.projects.forEach(project => {
    const card = document.createElement("button"); card.className = "project-card"; card.type = "button";
    const count = Object.keys(project.files || {}).length;
    card.innerHTML = '<div class="project-card-top"><span>▧</span><span>↗</span></div><div><h3></h3><p></p></div>';
    card.querySelector("h3").textContent = project.name;
    card.querySelector("p").textContent = count + " files · Edited " + new Date(project.updatedAt || project.createdAt).toLocaleDateString();
    card.addEventListener("click", () => switchProject(project.id)); grid.appendChild(card);
  });
}
function renderFiles() {
  const p = getProject(); if (!p) return;
  const list = $("file-list"); list.innerHTML = "";
  const paths = Object.keys(p.files || {}).sort((a,b) => a.localeCompare(b));
  $("file-count").textContent = paths.length;
  paths.forEach(path => {
    const b = document.createElement("button"); b.className = "file-item" + (state.selectedFile === path ? " active" : ""); b.textContent = path;
    b.addEventListener("click", () => { state.selectedFile = path; renderFiles(); }); list.appendChild(b);
  });
  const file = state.selectedFile && p.files[state.selectedFile] !== undefined ? state.selectedFile : paths[0];
  state.selectedFile = file || null;
  $("editor-filename").textContent = file || "Select a file";
  $("code-editor").value = file ? p.files[file] : "";
  $("code-editor").disabled = !file;
  $("save-file-button").disabled = !file;
  $("download-file-button").disabled = !file;
  $("editor-status").textContent = file ? "Ready · changes save when you tap Save" : "No file selected";
}
function saveEditor() {
  const p = getProject(); if (!p || !state.selectedFile) return;
  p.files[state.selectedFile] = $("code-editor").value; updateProject(); renderFiles(); toast("Saved " + state.selectedFile);
}
function renderChat() {
  const p = getProject(); const box = $("chat-messages"); box.innerHTML = "";
  if (!p || !p.messages.length) {
    box.innerHTML = '<div class="chat-welcome"><div class="ai-orb">✦</div><h2>What are we building?</h2><p>Ask for a website, a new feature, or changes to your existing project.</p><div class="suggestions"><button class="suggestion">Create a modern login page</button><button class="suggestion">Make it mobile responsive</button><button class="suggestion">Add a navigation bar</button></div></div>';
    box.querySelectorAll(".suggestion").forEach(b => b.addEventListener("click", () => { $("chat-input").value = b.textContent; $("chat-input").focus(); }));
    return;
  }
  p.messages.forEach(m => {
    const item = document.createElement("div"); item.className = "message " + (m.role === "user" ? "user" : "assistant");
    const role = document.createElement("div"); role.className = "message-role"; role.textContent = m.role === "user" ? "YOU" : "AI BUILDER";
    const body = document.createElement("div"); body.className = "message-body"; body.textContent = m.content || "";
    item.append(role, body);
    if (m.changes?.length) {
      const changes = document.createElement("div"); changes.className = "change-list";
      m.changes.forEach(c => { const line = document.createElement("div"); line.className = "change-item"; line.textContent = (c.action === "created" ? "＋ " : "↻ ") + c.path + " " + (c.action === "created" ? "created" : "updated"); changes.appendChild(line); });
      item.appendChild(changes);
    }
    box.appendChild(item);
  });
  box.scrollTop = box.scrollHeight;
}
function addMessage(role, content, changes) {
  const p = getProject(); if (!p) return;
  p.messages.push({ role, content, changes: changes || [], time: Date.now() }); updateProject(); renderChat();
}
function setActivity(text) { $("activity-line").textContent = text || ""; }
function setBusy(value) { state.busy = value; $("send-button").disabled = value; $("chat-input").disabled = value; }

function safePath(path) {
  if (typeof path !== "string") return null;
  const clean = path.trim().replace(/^\/+/, "").replace(/\\/g, "/");
  if (!clean || clean.split("/").some(part => !part || part === "." || part === "..") || clean.length > 180) return null;
  return clean;
}
function parseAIResponse(raw) {
  let text = raw.trim().replace(/^\`\`\`(?:json)?\s*/i, "").replace(/\s*\`\`\`$/, "");
  try { return JSON.parse(text); } catch {}
  const start = text.indexOf("{"); const end = text.lastIndexOf("}");
  if (start >= 0 && end > start) { try { return JSON.parse(text.slice(start, end + 1)); } catch {} }
  return { message: raw, changes: [] };
}
function systemPrompt(project) {
  const files = Object.entries(project.files || {}).map(([path, content]) => ({ path, content: content.slice(0, 18000) }));
  return `You are HTML Make, a practical website-building coding assistant. You work on the user's current project and maintain continuity across the conversation. The project files are provided below.

Return ONLY valid JSON with this exact shape:
{"message":"A friendly concise progress summary for the user","changes":[{"path":"index.html","content":"complete file content","action":"created"}]}

Rules:
- When creating or editing a file, return its COMPLETE final content, not a patch or snippet.
- Use action "created" for new files and "updated" for existing files.
- Include every file needed for the requested feature. Use separate HTML, CSS, and JS files where appropriate.
- Keep existing project features unless the user asks to remove them.
- Make designs polished, responsive, accessible, and functional on phones.
- Use relative paths for project assets. Main page is index.html.
- Do not claim a file was changed unless it is included in changes.
- If no file edit is needed, return changes as [] and answer in message.
- Never put markdown fences around the JSON.
- Do not include API keys, credentials, or secrets in generated files.
- Keep the message natural, like "Creating the homepage..." or "Added the login form and validation." The app will display actual file creation/update events separately.

CURRENT PROJECT: ${project.name}
CURRENT FILES:
${JSON.stringify(files)}
`;
}
async function askGroq(userText) {
  const key = localStorage.getItem(API_KEY_STORE);
  if (!key) { $("key-dialog").showModal(); throw new Error("Add your Groq API key to start chatting."); }
  const p = getProject(); if (!p) throw new Error("Create or open a project first.");
  const history = [{ role: "system", content: systemPrompt(p) }];
  p.messages.slice(-18).forEach(m => history.push({ role: m.role, content: m.content }));
  history.push({ role: "user", content: userText });
  const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": "Bearer " + key },
    body: JSON.stringify({ model: MODEL, messages: history, temperature: 0.35, max_tokens: 12000, reasoning_effort: "medium", response_format: { type: "json_object" } })
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error?.message || "Groq request failed (" + response.status + ").");
  const raw = data.choices?.[0]?.message?.content;
  if (!raw) throw new Error("The AI returned an empty response.");
  return parseAIResponse(raw);
}
async function sendChat(text) {
  const prompt = text.trim(); if (!prompt || state.busy) return;
  if (!localStorage.getItem(API_KEY_STORE)) { $("key-dialog").showModal(); toast("Add your Groq API key first"); return; }
  addMessage("user", prompt); $("chat-input").value = ""; setBusy(true); setActivity("Thinking...");
  try {
    const result = await askGroq(prompt); setActivity("Working...");
    const p = getProject(); const changes = [];
    if (Array.isArray(result.changes)) {
      for (const change of result.changes) {
        const path = safePath(change.path);
        if (!path || typeof change.content !== "string") continue;
        const existed = Object.prototype.hasOwnProperty.call(p.files, path);
        p.files[path] = change.content;
        changes.push({ path, action: existed ? "updated" : "created" });
        setActivity((existed ? "Updating " : "Creating ") + path + "...");
        renderFiles();
      }
    }
    updateProject();
    const message = typeof result.message === "string" ? result.message : "Finished updating your project.";
    addMessage("assistant", message, changes);
    if (changes.length) { setActivity(changes.map(c => c.path + " " + c.action).join(" · ")); renderFiles(); }
    else setActivity("");
    toast(changes.length ? changes.length + " file(s) updated" : "AI replied");
  } catch (error) {
    addMessage("assistant", "I couldn't complete that request: " + error.message);
    setActivity(""); toast(error.message.slice(0, 90));
  } finally { setBusy(false); $("chat-input").focus(); }
}

function buildPreview() {
  const p = getProject(); if (!p) return "<h2>No project open</h2>";
  let html = p.files["index.html"] || "<h2>index.html not found</h2>";
  const css = p.files["style.css"] || p.files["styles.css"] || "";
  const js = p.files["app.js"] || p.files["script.js"] || "";
  if (css) {
    const style = "<style>\n" + css + "\n</style>";
    if (/<\/head>/i.test(html)) html = html.replace(/<\/head>/i, style + "</head>");
    else html = style + html;
  }
  if (js) {
    const script = "<script>\n" + js.replace(/<\/script/gi, "<\\/script") + "\n<\/script>";
    if (/<\/body>/i.test(html)) html = html.replace(/<\/body>/i, script + "</body>");
    else html += script;
  }
  return html;
}
function renderPreview() {
  const p = getProject(); $("preview-title").textContent = p?.name || "Project preview";
  $("preview-frame").srcdoc = buildPreview();
}
function downloadText(name, content, type = "text/plain") {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement("a"); a.href = url; a.download = name.split("/").pop(); a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function importFiles(files) {
  const p = getProject(); if (!p) return;
  let count = 0;
  Array.from(files).forEach(file => {
    if (file.size > 2 * 1024 * 1024) { toast(file.name + " is too large"); return; }
    const reader = new FileReader();
    reader.onload = () => { p.files[safePath(file.webkitRelativePath || file.name) || file.name] = String(reader.result); updateProject(); renderFiles(); count++; };
    reader.readAsText(file);
  });
  toast("Importing selected files...");
}

$("new-project-button").addEventListener("click", () => { $("project-name-input").value = ""; $("project-dialog").showModal(); $("project-name-input").focus(); });
$("project-form").addEventListener("submit", e => { e.preventDefault(); const name = $("project-name-input").value.trim(); if (name) { createProject(name); $("project-dialog").close(); navigate("aichat"); } });
$("new-file-button").addEventListener("click", () => { if (!getProject()) return; $("new-file-name").value = ""; $("file-dialog").showModal(); });
$("file-form").addEventListener("submit", e => {
  e.preventDefault(); const path = safePath($("new-file-name").value); const p = getProject();
  if (!path || !p) return toast("Enter a valid file path");
  if (p.files[path] !== undefined) return toast("That file already exists");
  p.files[path] = ""; state.selectedFile = path; updateProject(); $("file-dialog").close(); renderFiles(); toast(path + " created");
});
$("save-file-button").addEventListener("click", saveEditor);
$("download-file-button").addEventListener("click", () => { if (state.selectedFile) downloadText(state.selectedFile, $("code-editor").value); });
$("code-editor").addEventListener("input", () => { $("editor-status").textContent = "Unsaved changes"; });
$("chat-form").addEventListener("submit", e => { e.preventDefault(); sendChat($("chat-input").value); });
$("chat-input").addEventListener("keydown", e => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); $("chat-form").requestSubmit(); } });
$("clear-chat-button").addEventListener("click", () => { const p = getProject(); if (!p || !confirm("Start a new chat? Your project files will stay unchanged.")) return; p.messages = []; updateProject(); renderChat(); });
$("refresh-preview-button").addEventListener("click", () => { renderPreview(); toast("Preview refreshed"); });
$("open-preview-button").addEventListener("click", () => { const w = window.open("", "_blank"); if (!w) return toast("Allow pop-ups to open preview"); w.document.open(); w.document.write(buildPreview()); w.document.close(); });
$("api-key-button").addEventListener("click", () => { $("api-key-input").value = localStorage.getItem(API_KEY_STORE) || ""; $("key-dialog").showModal(); });
$("key-form").addEventListener("submit", e => { e.preventDefault(); const key = $("api-key-input").value.trim(); if (!key) return; localStorage.setItem(API_KEY_STORE, key); $("key-dialog").close(); toast("Groq key saved on this device"); });
$("remove-key-button").addEventListener("click", () => { localStorage.removeItem(API_KEY_STORE); $("api-key-input").value = ""; $("key-dialog").close(); toast("API key removed"); });
$("import-project-button").addEventListener("click", () => $("import-project-input").click());
$("import-project-input").addEventListener("change", e => { const files = e.target.files; if (!files?.length) return; importFiles(files); e.target.value = ""; });
document.querySelectorAll("[data-page]").forEach(b => b.addEventListener("click", () => navigate(b.dataset.page)));
window.addEventListener("beforeunload", () => { if (state.previewUrl) URL.revokeObjectURL(state.previewUrl); });

load(); renderAll(); navigate("home");
