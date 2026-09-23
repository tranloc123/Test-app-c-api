(() => {
  if (window.__SCBD_DEV_BOOTSTRAP__) return;
  window.__SCBD_DEV_BOOTSTRAP__ = true;

  const dispatch = (name, detail) => {
    try {
      window.dispatchEvent(new CustomEvent(name, { detail }));
    } catch (_) {}
  };

  const toFullBinary = (ab) => {
    try {
      const u8 = new Uint8Array(ab);
      const owned = u8.slice().buffer;
      return {
        len: u8.byteLength,
        sampleBytes: u8.byteLength,
        full: true,
        buffer: owned
      };
    } catch (e) {
      return { error: String(e) };
    }
  };

  // Generic WebSocket tap. It does not log by itself.
  try {
    const NativeWS = window.WebSocket;
    if (NativeWS && !NativeWS.__SCBD_WRAPPED__) {
      class SCBDWebSocket extends NativeWS {
        constructor(...args) {
          super(...args);

          try {
            dispatch("__SCBD_WS_META__", {
              event: "connect",
              url: String(args[0] || "")
            });
          } catch (_) {}

          this.addEventListener("open", () => {
            dispatch("__SCBD_WS_META__", {
              event: "open",
              url: this.url || String(args[0] || "")
            });
          });

          this.addEventListener("close", (e) => {
            dispatch("__SCBD_WS_META__", {
              event: "close",
              url: this.url || String(args[0] || ""),
              code: e.code,
              reason: e.reason || ""
            });
          });

          this.addEventListener("message", async (e) => {
            try {
              if (typeof e.data === "string") {
                dispatch("__SCBD_WS_MESSAGE__", {
                  kind: "text",
                  data: e.data
                });
                return;
              }

              if (e.data instanceof ArrayBuffer) {
                dispatch("__SCBD_WS_MESSAGE__", {
                  kind: "binary",
                  data: toFullBinary(e.data)
                });
                return;
              }

              if (ArrayBuffer.isView(e.data)) {
                const ab = e.data.buffer.slice(
                  e.data.byteOffset,
                  e.data.byteOffset + e.data.byteLength
                );
                dispatch("__SCBD_WS_MESSAGE__", {
                  kind: "binary",
                  data: toFullBinary(ab)
                });
                return;
              }

              if (typeof Blob !== "undefined" && e.data instanceof Blob) {
                const ab = await e.data.arrayBuffer();
                dispatch("__SCBD_WS_MESSAGE__", {
                  kind: "binary",
                  data: toFullBinary(ab)
                });
                return;
              }

              dispatch("__SCBD_WS_MESSAGE__", {
                kind: "other",
                data: Object.prototype.toString.call(e.data)
              });
            } catch (err) {
              dispatch("__SCBD_BOOT_ERR__", "ws-message " + err);
            }
          });
        }
      }

      try {
        Object.defineProperty(SCBDWebSocket, "__SCBD_WRAPPED__", { value: true });
        Object.defineProperty(SCBDWebSocket, "CONNECTING", { value: NativeWS.CONNECTING });
        Object.defineProperty(SCBDWebSocket, "OPEN", { value: NativeWS.OPEN });
        Object.defineProperty(SCBDWebSocket, "CLOSING", { value: NativeWS.CLOSING });
        Object.defineProperty(SCBDWebSocket, "CLOSED", { value: NativeWS.CLOSED });
      } catch (_) {}

      window.WebSocket = SCBDWebSocket;
    }
  } catch (e) {
    dispatch("__SCBD_BOOT_ERR__", "ws-hook " + e);
  }

  // Generic fetch metadata tap. No response body is read here.
  try {
    const nativeFetch = window.fetch;
    if (nativeFetch && !nativeFetch.__SCBD_WRAPPED__) {
      const wrappedFetch = function(...args) {
        let url = "";
        try {
          url = typeof args[0] === "string"
            ? args[0]
            : ((args[0] && args[0].url) || "");
          dispatch("__SCBD_FETCH_META__", {
            event: "request",
            url
          });
        } catch (_) {}

        const p = nativeFetch.apply(this, args);
        p.then((res) => {
          try {
            dispatch("__SCBD_FETCH_META__", {
              event: "response",
              url,
              status: res.status,
              contentType:
                (res.headers && res.headers.get &&
                  res.headers.get("content-type")) || ""
            });
          } catch (_) {}
        }).catch(() => {});
        return p;
      };

      try {
        Object.defineProperty(wrappedFetch, "__SCBD_WRAPPED__", { value: true });
      } catch (_) {}
      window.fetch = wrappedFetch;
    }
  } catch (e) {
    dispatch("__SCBD_BOOT_ERR__", "fetch-hook " + e);
  }

  // Generic XHR metadata tap.
  try {
    const XHR = window.XMLHttpRequest;
    if (XHR && !XHR.prototype.__SCBD_WRAPPED__) {
      const nativeOpen = XHR.prototype.open;
      const nativeSend = XHR.prototype.send;

      XHR.prototype.open = function(method, url, ...rest) {
        try {
          this.__scbd_url = String(url || "");
          this.__scbd_method = String(method || "GET");
          dispatch("__SCBD_XHR_META__", {
            event: "request",
            method: this.__scbd_method,
            url: this.__scbd_url
          });
        } catch (_) {}
        return nativeOpen.call(this, method, url, ...rest);
      };

      XHR.prototype.send = function(...args) {
        try {
          this.addEventListener("load", () => {
            try {
              dispatch("__SCBD_XHR_META__", {
                event: "response",
                method: this.__scbd_method || "GET",
                url: this.__scbd_url || "",
                status: this.status
              });
            } catch (_) {}
          });
        } catch (_) {}
        return nativeSend.apply(this, args);
      };

      try {
        Object.defineProperty(XHR.prototype, "__SCBD_WRAPPED__", { value: true });
      } catch (_) {}
    }
  } catch (e) {
    dispatch("__SCBD_BOOT_ERR__", "xhr-hook " + e);
  }

  try {
    SCBD.report("BOOT", "document-start hooks installed");
  } catch (_) {}
})();
