document.addEventListener("DOMContentLoaded", () => {

  const BASE_URL = window.location.origin;

  // Função para mostrar alertas Bootstrap
  function showAlert(message, type = "info") {
    const resultArea = document.getElementById("resultArea");
    if (!resultArea) return;

    resultArea.innerHTML = `
      <div class="alert alert-${type} alert-dismissible fade show" role="alert">
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
      </div>
    `;
  }

  // Helper para requisições JSON
  async function fetchJson(url, options = {}) {
    const res = await fetch(url, { credentials: "include", ...options });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();
  }

  // Login com Google
  const loginBtn = document.getElementById("loginBtn");
  if (loginBtn) {
    loginBtn.addEventListener("click", () => {
      window.location.href = `${BASE_URL}/oauth2/authorization/google`;
    });
  }

  // Logout
  const logoutBtn = document.getElementById("logoutBtn");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", () => {
      window.location.href = `${BASE_URL}/logout`;
    });
  }

  // Abrir email em nova aba
  async function abrirEmail(id) {
    try {
      const data = await fetchJson(`${BASE_URL}/gmail/${id}`);

      const newWin = window.open("", "_blank", "width=800,height=600");
      if (!newWin) {
        alert("⚠️ Bloqueador de pop-ups ativo. Libere para abrir emails.");
        return;
      }

      const safeBody = data.body ? encodeURIComponent(data.body) : "";

      newWin.document.write(`
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
          <meta charset="UTF-8">
          <title>${data.subject || "Email"}</title>
          <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
        </head>
        <body class="bg-light p-4">
          <div class="card shadow">
            <div class="card-body">
              <h3 class="text-primary">${data.subject || "(sem assunto)"}</h3>
              <p><strong>De:</strong> ${data.from || "(desconhecido)"}</p>
              <hr>
              <iframe style="width:100%;height:400px;border:none;" srcdoc="${safeBody}"></iframe>
              <hr>
              <h5>Anexos:</h5>
              <ul>
                ${
                  (data.attachments && data.attachments.length > 0)
                    ? data.attachments.map(att => `
                        <li>
                          📎 ${att.filename} (${att.mimeType}, ${att.size} bytes)
                          <br>
                          <a href="data:${att.mimeType};base64,${att.contentBase64}" download="${att.filename}">
                            ⬇️ Download
                          </a>
                        </li>
                      `).join("")
                    : "<li>Nenhum anexo</li>"
                }
              </ul>
            </div>
          </div>
        </body>
        </html>
      `);

      newWin.document.close();

    } catch (err) {
      showAlert("❌ Erro ao abrir email: " + err.message, "danger");
    }
  }

  // Ler email em modal Bootstrap
  async function lerEmail(id) {
    try {
      const data = await fetchJson(`${BASE_URL}/gmail/${id}`);

      const bodyContent = data.body?.includes("<") 
        ? data.body 
        : (data.body || "").replace(/\n/g, "<br>");

      document.getElementById("emailModalTitle").innerText = data.subject || "(sem assunto)";
      document.getElementById("emailModalBody").innerHTML = bodyContent;

      new bootstrap.Modal(document.getElementById("emailModal")).show();

    } catch (err) {
      showAlert("❌ Erro ao ler email: " + err.message, "danger");
    }
  }

  // Apagar email com feedback detalhado
  async function apagarEmail(id) {
    if (!confirm("Tem certeza que deseja apagar este email?")) return;
    try {
      const res = await fetch(`${BASE_URL}/gmail/${id}`, { method: "DELETE", credentials: "include" });

      if (res.status === 401) {
        showAlert("⚠️ Sessão expirada. Faça login novamente.", "warning");
        return;
      }
      if (res.status === 403) {
        showAlert("⚠️ Permissão insuficiente. Autorize novamente o acesso ao Gmail.", "warning");
        return;
      }
      if (res.status === 404) {
        showAlert("📭 Mensagem já apagada ou não encontrada.", "info");
        return;
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      showAlert("🗑️ Email apagado com sucesso!", "success");
      document.getElementById("inboxBtn").click();

    } catch (err) {
      showAlert("❌ Erro ao apagar email: " + err.message, "danger");
    }
  }

  // Inbox
  const inboxBtn = document.getElementById("inboxBtn");
  if (inboxBtn) {
    inboxBtn.addEventListener("click", async () => {
      showAlert("⏳ Carregando inbox...", "info");

      try {
        const data = await fetchJson(`${BASE_URL}/gmail/inbox`);
        const tbody = document.querySelector("#inboxTable tbody");
        if (!tbody) return;

        tbody.innerHTML = "";

        if (Array.isArray(data) && data.length > 0) {
          data.forEach(msg => {
            const row = document.createElement("tr");
            row.innerHTML = `
              <td>${msg.from || ""}</td>
              <td>${msg.subject || ""}</td>
              <td>${msg.snippet || ""}</td>
              <td>
                <button class="btn btn-sm btn-primary" onclick="abrirEmail('${msg.id}')" aria-label="Abrir email">📖 Abrir</button>
                <button class="btn btn-sm btn-info text-white" onclick="lerEmail('${msg.id}')" aria-label="Ler email">👁️ Ler</button>
                <button class="btn btn-sm btn-danger" onclick="apagarEmail('${msg.id}')" aria-label="Apagar email">🗑️ Apagar</button>
              </td>
            `;
            tbody.appendChild(row);
          });

          showAlert("📥 Inbox carregada com sucesso!", "success");

        } else {
          showAlert("Nenhuma mensagem encontrada.", "warning");
        }

      } catch (err) {
        showAlert("❌ Erro ao buscar inbox: " + err.message, "danger");
      }
    });
  }

  // Enviar email
  const sendForm = document.getElementById("sendForm");
  if (sendForm) {
    sendForm.addEventListener("submit", async (e) => {
      e.preventDefault();

      const to = document.getElementById("to")?.value;
      const subject = document.getElementById("subject")?.value;
      const body = document.getElementById("body")?.value;

      if (!to || !subject || !body) {
        showAlert("⚠️ Preencha todos os campos antes de enviar.", "warning");
        return;
      }

      const sendButton = sendForm.querySelector("button[type=submit]");
      sendButton.disabled = true;
      sendButton.innerText = "Enviando...";

      try {
        const res = await fetch(`${BASE_URL}/gmail/send`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ to, subject, body })
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const result = await res.json();

        if (result.status === "success") {
          showAlert(result.message, "success");
          sendForm.reset();
        } else {
          showAlert(result.error || "Erro desconhecido ao enviar email.", "danger");
        }

      } catch (err) {
        showAlert("❌ Erro ao enviar email: " + err.message, "danger");
      } finally {
        sendButton.disabled = false;
        sendButton.innerText = "Enviar";
      }
    });
  }

  // Expondo funções globalmente
  window.abrirEmail = abrirEmail;
  window.lerEmail = lerEmail;
  window.apagarEmail = apagarEmail;

});
