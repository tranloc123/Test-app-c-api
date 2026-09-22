(() => {
  if (window.__SCBD_LIVE_PROBE_INSTALLED__) {
    try { SCBD.report("HOOK", "already-installed " + location.href); } catch (_) {}
    return;
  }
  window.__SCBD_LIVE_PROBE_INSTALLED__ = true;

  const MAX_TEXT = 12000;
  const MAX_BIN = 2048;
  const sentDom = new Map();

  function safeString(v) {
    try {
      if (typeof v === "string") return v;
      return JSON.stringify(v);
    } catch (_) {
      try { return String(v); } catch (_) { return "[unprintable]"; }
    }
  }

  function send(kind, payload) {
    try {
      let s = safeString(payload);
      if (s.length > MAX_TEXT) s = s.slice(0, MAX_TEXT) + "…[cut]";
      SCBD.report(String(kind), s);
    } catch (_) {}
  }

  function interestingUrl(url) {
    const s = String(url || "").toLowerCase();
    return /webcast|live|room|gift|comment|rank|viewer|im\/fetch|message|event/.test(s);
  }

  function bytesToB64(ab) {
    try {
      const u8 = new Uint8Array(ab);
      const n = Math.min(u8.length, MAX_BIN);
      let out = "";
      for (let i = 0; i < n; i++) out += String.fromCharCode(u8[i]);
      return {
        len: u8.length,
        sampleBytes: n,
        b64: btoa(out)
      };
    } catch (e) {
      return { error: String(e) };
    }
  }

  async function reportWsData(data) {
    try {
      if (typeof data === "string") {
        send("WS_TEXT", data);
        return;
      }
      if (data instanceof ArrayBuffer) {
        send("WS_BIN", bytesToB64(data));
        return;
      }
      if (ArrayBuffer.isView(data)) {
        const ab = data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength);
        send("WS_BIN", bytesToB64(ab));
        return;
      }
      if (typeof Blob !== "undefined" && data instanceof Blob) {
        const ab = await data.arrayBuffer();
        send("WS_BIN", bytesToB64(ab));
        return;
      }
      send("WS_OTHER", Object.prototype.toString.call(data));
    } catch (e) {
      send("JS_ERR", "reportWsData " + e);
    }
  }

  // ---- WebSocket hook ----
  try {
    const NativeWS = window.WebSocket;
    if (NativeWS) {
      class ProbeWebSocket extends NativeWS {
        constructor(...args) {
          super(...args);
          try { send("WS_CONNECT", String(args[0] || "")); } catch (_) {}

          this.addEventListener("open", () => {
            try { send("WS_OPEN", this.url || String(args[0] || "")); } catch (_) {}
          });

          this.addEventListener("message", (e) => {
            reportWsData(e.data);
          });

          this.addEventListener("close", (e) => {
            send("WS_CLOSE", {
              url: this.url || String(args[0] || ""),
              code: e.code,
              reason: e.reason || ""
            });
          });

          this.addEventListener("error", () => {
            send("WS_ERROR", this.url || String(args[0] || ""));
          });
        }
      }
      try {
        Object.defineProperty(ProbeWebSocket, "CONNECTING", { value: NativeWS.CONNECTING });
        Object.defineProperty(ProbeWebSocket, "OPEN", { value: NativeWS.OPEN });
        Object.defineProperty(ProbeWebSocket, "CLOSING", { value: NativeWS.CLOSING });
        Object.defineProperty(ProbeWebSocket, "CLOSED", { value: NativeWS.CLOSED });
      } catch (_) {}
      window.WebSocket = ProbeWebSocket;
      send("HOOK", "WebSocket hooked");
    }
  } catch (e) {
    send("JS_ERR", "WebSocket hook " + e);
  }

  // ---- fetch hook ----
  try {
    const nativeFetch = window.fetch;
    if (nativeFetch) {
      window.fetch = function(...args) {
        let url = "";
        try {
          url = typeof args[0] === "string" ? args[0] : (args[0] && args[0].url) || "";
          if (interestingUrl(url)) send("FETCH_REQ", url);
        } catch (_) {}

        const p = nativeFetch.apply(this, args);
        p.then((res) => {
          try {
            if (!interestingUrl(url)) return;
            send("FETCH_RES", { url, status: res.status, type: res.type });
            const ct = (res.headers && res.headers.get && res.headers.get("content-type")) || "";
            if (/json|text|javascript/i.test(ct)) {
              res.clone().text().then((t) => {
                if (t) send("FETCH_BODY", { url, text: t.slice(0, MAX_TEXT) });
              }).catch(() => {});
            }
          } catch (_) {}
        }).catch(() => {});
        return p;
      };
      send("HOOK", "fetch hooked");
    }
  } catch (e) {
    send("JS_ERR", "fetch hook " + e);
  }

  // ---- XMLHttpRequest hook ----
  try {
    const XHR = window.XMLHttpRequest;
    if (XHR) {
      const nativeOpen = XHR.prototype.open;
      const nativeSend = XHR.prototype.send;

      XHR.prototype.open = function(method, url, ...rest) {
        try {
          this.__scbd_url = String(url || "");
          this.__scbd_method = String(method || "GET");
          if (interestingUrl(this.__scbd_url)) {
            send("XHR_REQ", { method: this.__scbd_method, url: this.__scbd_url });
          }
        } catch (_) {}
        return nativeOpen.call(this, method, url, ...rest);
      };

      XHR.prototype.send = function(...args) {
        try {
          this.addEventListener("load", () => {
            try {
              if (!interestingUrl(this.__scbd_url)) return;
              send("XHR_RES", {
                method: this.__scbd_method,
                url: this.__scbd_url,
                status: this.status
              });

              if (this.responseType === "" || this.responseType === "text") {
                const txt = this.responseText || "";
                if (txt) send("XHR_BODY", { url: this.__scbd_url, text: txt.slice(0, MAX_TEXT) });
              }
            } catch (_) {}
          });
        } catch (_) {}
        return nativeSend.apply(this, args);
      };
      send("HOOK", "XHR hooked");
    }
  } catch (e) {
    send("JS_ERR", "XHR hook " + e);
  }

  // ---- DOM observer fallback ----
  function reportDomText(text) {
    try {
      text = String(text || "").replace(/\s+/g, " ").trim();
      if (text.length < 2 || text.length > 700) return;

      const now = Date.now();
      const prev = sentDom.get(text) || 0;
      if (now - prev < 5000) return;
      sentDom.set(text, now);

      if (sentDom.size > 350) {
        const cutoff = now - 30000;
        for (const [k, t] of sentDom.entries()) {
          if (t < cutoff) sentDom.delete(k);
        }
      }

      send("DOM", text);
    } catch (_) {}
  }

  function inspectNode(node) {
    try {
      if (!node) return;
      if (node.nodeType === Node.TEXT_NODE) {
        reportDomText(node.textContent);
        return;
      }
      if (node.nodeType !== Node.ELEMENT_NODE) return;

      const el = node;
      const txt = el.innerText || el.textContent || "";
      reportDomText(txt);
    } catch (_) {}
  }

  function startObserver() {
    try {
      const target = document.documentElement || document.body;
      if (!target) return false;

      const mo = new MutationObserver((mutations) => {
        for (const m of mutations) {
          for (const node of m.addedNodes || []) inspectNode(node);
          if (m.type === "characterData") inspectNode(m.target);
        }
      });

      mo.observe(target, {
        childList: true,
        subtree: true,
        characterData: true
      });

      send("HOOK", "MutationObserver started");
      return true;
    } catch (e) {
      send("JS_ERR", "observer " + e);
      return false;
    }
  }

  if (!startObserver()) {
    document.addEventListener("DOMContentLoaded", startObserver, { once: true });
  }

  // ---- Resource timing watcher: useful if TikTok does not use window.WebSocket ----
  try {
    let seenPerf = new Set();
    setInterval(() => {
      try {
        const entries = performance.getEntriesByType("resource") || [];
        for (const e of entries) {
          const n = String(e.name || "");
          if (!interestingUrl(n) || seenPerf.has(n)) continue;
          seenPerf.add(n);
          send("RESOURCE", {
            name: n,
            initiatorType: e.initiatorType || "",
            duration: Math.round(e.duration || 0)
          });
        }
        if (seenPerf.size > 500) seenPerf = new Set(Array.from(seenPerf).slice(-250));
      } catch (_) {}
    }, 3000);
  } catch (_) {}

  send("HOOK", "SCBD probe installed at " + location.href);
})();
