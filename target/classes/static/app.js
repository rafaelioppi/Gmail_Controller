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
        
        // Se a resposta for OK (200-299), retorna o JSON
        if (res.ok) {
            return res.json();
        }
        
        // Trata erros:
        
        // Tente extrair o corpo do erro (se JSON). O back-end envia JSON em caso de erro.
        const errorBody = await res.json().catch(() => ({})); 
        
        let errorMessage = errorBody.error;

        // Se o back-end não forneceu uma mensagem de erro clara, use o status HTTP
        if (!errorMessage) {
            if (res.status === 404) {
                errorMessage = "Recurso não encontrado (404).";
            } else {
                errorMessage = `Erro de HTTP: ${res.status}`;
            }
        }
        
        // Lança a mensagem de erro que será capturada nos blocos try/catch
        throw new Error(errorMessage);
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

                        const abrirBtn = document.createElement("button");
                        abrirBtn.className = "btn btn-sm btn-primary";
                        abrirBtn.innerText = "📖 Abrir";
                        abrirBtn.addEventListener("click", () => abrirEmail(msg.id));

                        const lerBtn = document.createElement("button");
                        lerBtn.className = "btn btn-sm btn-info text-white";
                        lerBtn.innerText = "👁️ Ler";
                        lerBtn.addEventListener("click", () => lerEmail(msg.id));

                        const apagarBtn = document.createElement("button");
                        apagarBtn.className = "btn btn-sm btn-danger";
                        apagarBtn.innerText = "🗑️ Apagar";
                        apagarBtn.addEventListener("click", () => apagarEmail(msg.id));

                        row.innerHTML = `
                            <td>${msg.from || ""}</td>
                            <td>${msg.subject || ""}</td>
                            <td>${msg.snippet || ""}</td>
                        `;
                        const tdActions = document.createElement("td");
                        tdActions.appendChild(abrirBtn);
                        tdActions.appendChild(lerBtn);
                        tdActions.appendChild(apagarBtn);
                        row.appendChild(tdActions);

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

    // Abrir email em nova aba
    async function abrirEmail(id) {
        try {
            const data = await fetchJson(`${BASE_URL}/gmail/${id}`);

            const newWin = window.open("", "_blank", "width=800,height=600");
            if (!newWin) {
                alert("⚠️ Bloqueador de pop-ups ativo. Libere para abrir emails.");
                return;
            }

            const safeBody = data.body 
                ? data.body.replace(/"/g, '&quot;').replace(/'/g, '&#39;') 
                : "";

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

            let bodyContent = "";
            if (data.body) {
                if (/<[a-z][\s\S]*>/i.test(data.body)) {
                    bodyContent = data.body;
                } else {
                    bodyContent = data.body.replace(/\n/g, "<br>");
                }
            }

            const safeSubject = (data.subject || "(sem assunto)")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;");

            const attachmentsHtml = (data.attachments && data.attachments.length > 0)
                ? data.attachments.map(att => `
                    <li>
                        📎 ${att.filename} (${att.mimeType}, ${att.size} bytes)
                        <br>
                        <a href="data:${att.mimeType};base64,${att.contentBase64}" download="${att.filename}">
                            ⬇️ Download
                        </a>
                    </li>
                `).join("")
                : "<li>Nenhum anexo</li>";

            document.getElementById("emailModalTitle").innerHTML = safeSubject;
            document.getElementById("emailModalBody").innerHTML = `
                <div>
                    ${bodyContent || "(sem conteúdo)"}
                    <hr>
                    <h5>Anexos:</h5>
                    <ul>${attachmentsHtml}</ul>
                </div>
            `;

            new bootstrap.Modal(document.getElementById("emailModal")).show();

        } catch (err) {
            showAlert("❌ Erro ao ler email: " + err.message, "danger");
        }
    }

    // Apagar email
    async function apagarEmail(id) {
        if (!confirm("Tem certeza que deseja apagar este email? Ele será excluído permanentemente.")) return;
        
        try {
            const res = await fetch(`${BASE_URL}/gmail/${id}`, { 
                method: "DELETE", 
                credentials: "include" 
            });

            if (res.status === 401) {
                showAlert("⚠️ Sessão expirada. Faça login novamente.", "warning");
                return;
            }
            
            // Tenta ler o corpo da resposta (pode ser JSON ou vazio)
            let responseBody = {};
            if (res.headers.get("Content-Type")?.includes("application/json")) {
                responseBody = await res.json().catch(() => ({})); // Adicionado catch para evitar falha se o corpo não for JSON
            }

            if (res.ok) {
                // Sucesso: Exibe mensagem e recarrega a inbox
                const successMessage = responseBody.message || "✅ Email apagado permanentemente com sucesso!";
                showAlert(successMessage, "success");
                
                // Chama a função que recarrega a inbox para atualizar a lista
                if (inboxBtn) {
                    inboxBtn.click();
                }

            } else if (res.status === 403) {
                showAlert("⚠️ Permissão insuficiente. Autorize novamente o acesso ao Gmail (escopo 'gmail.modify' necessário).", "warning");
            } else {
                // Trata outros erros HTTP (incluindo erros do back-end com corpo JSON)
                const errorMessage = responseBody.error || `Erro ao apagar: HTTP ${res.status}`;
                throw new Error(errorMessage);
            }

        } catch (err) {
            showAlert("❌ Erro ao apagar email: " + err.message, "danger");
        }
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

                // Se a resposta não for ok, tenta ler o erro do corpo ou usa status HTTP
                if (!res.ok) {
                     const errorBody = await res.json().catch(() => ({})); 
                     throw new Error(errorBody.error || `HTTP ${res.status}`);
                }

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