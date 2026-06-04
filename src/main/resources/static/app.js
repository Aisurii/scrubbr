"use strict";

const dropzone = document.getElementById("dropzone");
const fileInput = document.getElementById("fileInput");
const browseBtn = document.getElementById("browseBtn");
const statusEl = document.getElementById("status");
const reportEl = document.getElementById("report");
const summaryEl = document.getElementById("summary");
const metadataList = document.getElementById("metadataList");
const piiList = document.getElementById("piiList");
const cleanBtn = document.getElementById("cleanBtn");
const resetBtn = document.getElementById("resetBtn");

let currentFile = null;

// ---- Upload triggers -------------------------------------------------------

browseBtn.addEventListener("click", () => fileInput.click());
dropzone.addEventListener("click", (e) => {
    if (e.target !== browseBtn) fileInput.click();
});
fileInput.addEventListener("change", () => {
    if (fileInput.files.length) analyze(fileInput.files[0]);
});

["dragenter", "dragover"].forEach((evt) =>
    dropzone.addEventListener(evt, (e) => {
        e.preventDefault();
        dropzone.classList.add("dragover");
    })
);
["dragleave", "drop"].forEach((evt) =>
    dropzone.addEventListener(evt, (e) => {
        e.preventDefault();
        dropzone.classList.remove("dragover");
    })
);
dropzone.addEventListener("drop", (e) => {
    const file = e.dataTransfer.files[0];
    if (file) analyze(file);
});

resetBtn.addEventListener("click", reset);
cleanBtn.addEventListener("click", downloadCleaned);

// ---- Core flow -------------------------------------------------------------

async function analyze(file) {
    currentFile = file;
    reportEl.classList.add("hidden");
    showStatus(`<span class="spinner"></span> Analyzing <strong>${escapeHtml(file.name)}</strong>…`, false);

    try {
        const form = new FormData();
        form.append("file", file);
        const res = await fetch("/api/analyze", { method: "POST", body: form });
        const data = await res.json();

        if (!res.ok) {
            showStatus(data.error || "Something went wrong.", true);
            return;
        }
        renderReport(data);
    } catch (err) {
        showStatus("Could not reach the server: " + err.message, true);
    }
}

function renderReport(report) {
    statusEl.classList.add("hidden");
    summaryEl.textContent = report.summary;

    renderMetadata(report.metadata);
    renderPii(report.pii, report.fileKind);

    cleanBtn.disabled = !report.canClean;
    cleanBtn.textContent = report.fileKind === "PDF"
        ? "Download cleaned PDF (metadata stripped + PII redacted) ↓"
        : "Download cleaned file (metadata stripped) ↓";

    reportEl.classList.remove("hidden");
}

function renderMetadata(metadata) {
    if (!metadata || metadata.length === 0) {
        metadataList.innerHTML = `<div class="empty">No hidden metadata found. 🎉</div>`;
        return;
    }
    const order = { HIGH: 0, MEDIUM: 1, LOW: 2 };
    metadata.sort((a, b) => order[a.severity] - order[b.severity]);
    metadataList.innerHTML = metadata.map((m) => `
        <div class="item">
            <span class="badge ${m.severity.toLowerCase()}">${m.severity}</span>
            <div>
                <div class="label">${escapeHtml(m.label)}</div>
                <div class="value">${escapeHtml(m.value)}</div>
                <div class="group">${escapeHtml(m.group)}</div>
            </div>
        </div>`).join("");
}

function renderPii(pii, fileKind) {
    if (fileKind !== "PDF") {
        piiList.innerHTML = `<div class="empty">Content scanning applies to PDFs. Images are checked for metadata only.</div>`;
        return;
    }
    if (!pii || pii.length === 0) {
        piiList.innerHTML = `<div class="empty">No emails, phone numbers or card numbers found in the text. 🎉</div>`;
        return;
    }
    piiList.innerHTML = pii.map((p) => `
        <div class="item">
            <span class="badge high">PII</span>
            <div>
                <div class="label">${escapeHtml(p.type)}</div>
                <div class="value">${escapeHtml(p.value)} ${p.count > 1 ? `· ×${p.count}` : ""}</div>
                <div class="group">page ${p.page}</div>
            </div>
        </div>`).join("");
}

async function downloadCleaned() {
    if (!currentFile) return;
    const original = cleanBtn.textContent;
    cleanBtn.disabled = true;
    cleanBtn.textContent = "Cleaning…";

    try {
        const form = new FormData();
        form.append("file", currentFile);
        const res = await fetch("/api/clean", { method: "POST", body: form });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showStatus(data.error || "Could not clean the file.", true);
            return;
        }
        const blob = await res.blob();
        const name = filenameFromDisposition(res.headers.get("Content-Disposition"))
            || "scrubbed-" + currentFile.name;
        triggerDownload(blob, name);
    } catch (err) {
        showStatus("Download failed: " + err.message, true);
    } finally {
        cleanBtn.disabled = false;
        cleanBtn.textContent = original;
    }
}

// ---- Helpers ---------------------------------------------------------------

function showStatus(html, isError) {
    statusEl.innerHTML = html;
    statusEl.classList.toggle("error", !!isError);
    statusEl.classList.remove("hidden");
}

function reset() {
    currentFile = null;
    fileInput.value = "";
    reportEl.classList.add("hidden");
    statusEl.classList.add("hidden");
}

function triggerDownload(blob, name) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}

function filenameFromDisposition(header) {
    if (!header) return null;
    const match = /filename="?([^"]+)"?/.exec(header);
    return match ? match[1] : null;
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
