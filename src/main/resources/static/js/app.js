(function () {
  const baseUrlEl = document.getElementById("baseUrl");
  const authStatus = document.getElementById("authStatus");
  const convIdEl = document.getElementById("convId");
  const out = document.getElementById("out");

  function base() {
    let u = (baseUrlEl.value || "").trim();
    if (!u) u = window.location.origin;
    return u.replace(/\/$/, "");
  }

  function token() {
    return sessionStorage.getItem("vagent_token") || "";
  }

  function setToken(t) {
    if (t) sessionStorage.setItem("vagent_token", t);
    else sessionStorage.removeItem("vagent_token");
    refreshAuthUi();
  }

  function refreshAuthUi() {
    const t = token();
    authStatus.textContent = t ? "已登录（Token 在 sessionStorage）" : "未登录";
    authStatus.className = "status " + (t ? "" : "muted");
  }

  async function api(path, opts) {
    const headers = Object.assign({}, opts && opts.headers);
    headers["Content-Type"] = "application/json";
    if (token()) headers["Authorization"] = "Bearer " + token();
    const res = await fetch(base() + path, Object.assign({}, opts, { headers }));
    const text = await res.text();
    let body = text;
    try {
      body = text ? JSON.parse(text) : null;
    } catch (_) {}
    if (!res.ok) {
      const msg = body && body.message ? body.message : text || res.statusText;
      throw new Error(res.status + " " + msg);
    }
    return body;
  }

  function log(line) {
    out.textContent += line + "\n";
    out.scrollTop = out.scrollHeight;
  }

  document.getElementById("btnReg").onclick = async () => {
    const username = document.getElementById("regUser").value.trim();
    const password = document.getElementById("regPass").value;
    try {
      const data = await api("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      setToken(data.token);
      log("注册成功");
    } catch (e) {
      log("注册失败: " + e.message);
    }
  };

  document.getElementById("btnLogin").onclick = async () => {
    const username = document.getElementById("loginUser").value.trim();
    const password = document.getElementById("loginPass").value;
    try {
      const data = await api("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      setToken(data.token);
      log("登录成功");
    } catch (e) {
      log("登录失败: " + e.message);
    }
  };

  document.getElementById("btnNewConv").onclick = async () => {
    try {
      const data = await api("/api/v1/conversations", { method: "POST", body: "{}" });
      convIdEl.textContent = data.id || "";
      log("会话: " + (data.id || ""));
    } catch (e) {
      log("创建会话失败: " + e.message);
    }
  };

  document.getElementById("btnSend").onclick = async () => {
    const cid = (convIdEl.textContent || "").trim();
    if (!cid) {
      log("请先创建会话");
      return;
    }
    const message = document.getElementById("msg").value;
    if (!message.trim()) return;

    const url =
      base() +
      "/api/v1/conversations/" +
      encodeURIComponent(cid) +
      "/chat/stream";
    // EventSource 无法带 Authorization，使用 fetch 读 SSE 流
    out.textContent = "";
    log("请求中…");
    try {
      const res = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "text/event-stream",
          Authorization: "Bearer " + token(),
        },
        body: JSON.stringify({ message }),
      });
      if (!res.ok) {
        const t = await res.text();
        throw new Error(res.status + " " + t);
      }
      const reader = res.body.getReader();
      const dec = new TextDecoder();
      let buf = "";
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += dec.decode(value, { stream: true });
        const parts = buf.split("\n\n");
        buf = parts.pop() || "";
        for (const block of parts) {
          const lines = block.split("\n");
          for (const line of lines) {
            if (line.startsWith("data:")) {
              const json = line.slice(5).trim();
              try {
                const o = JSON.parse(json);
                log(JSON.stringify(o));
              } catch {
                log(json);
              }
            }
          }
        }
      }
      log("— 结束 —");
    } catch (e) {
      log("SSE 错误: " + e.message);
    }
  };

  baseUrlEl.value = window.location.origin;
  refreshAuthUi();
})();
