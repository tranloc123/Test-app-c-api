(() => {
  try {
    if (window.__SCBD_LOCAL_PROBE__ && window.__SCBD_LOCAL_PROBE__.dispose) {
      window.__SCBD_LOCAL_PROBE__.dispose();
    }
  } catch (_) {}

  const VERSION = "fallback-1";
  let active = false;
  let timer = null;

  const report = (k, v) => {
    try { SCBD.report(k, typeof v === "string" ? v : JSON.stringify(v)); } catch (_) {}
  };

  const onWs = (e) => {
    if (!active) return;
    const d = e.detail || {};
    report(d.kind === "binary" ? "WS_BIN" : "WS_TEXT", d.data || "");
  };

  window.addEventListener("__SCBD_WS_MESSAGE__", onWs);

  window.__SCBD_LOCAL_PROBE__ = {
    version: VERSION,
    startCapture(ms = 10000) {
      active = true;
      report("CAPTURE_START", { ms, version: VERSION });
      clearTimeout(timer);
      timer = setTimeout(() => this.stopCapture(), ms);
    },
    stopCapture() {
      if (!active) return;
      active = false;
      report("CAPTURE_END", { version: VERSION });
    },
    dispose() {
      active = false;
      clearTimeout(timer);
      window.removeEventListener("__SCBD_WS_MESSAGE__", onWs);
    }
  };

  report("PROBE_READY", VERSION);
})();
