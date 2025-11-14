const BASE_URL = "https://fantastic-space-pancake-v6vrv5wv4qxpcpgp-3335.app.github.dev";

// Função para mostrar alertas Bootstrap
function showAlert(message, type = "info") {
  const resultArea = document.getElementById("resultArea");
  resultArea.innerHTML = `
    <div class="alert alert-${type} alert-dismissible fade show" role="alert">
      ${message}
      <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
  `;
}

// Login com Google
document.getElementById("loginBtn").addEventListener("click", () => {
  window.location.href = `${BASE_URL}/oauth2/authorization/google`;
});

// Buscar inbox e preencher tabela
document.getElementById("inboxBtn").addEventListener("click", async () => {
  try {
    const res = await fetch(`${BASE_URL}/gmail/inbox`, { credentials: "include" });
    const data = await res.json();

    const tbody = document.querySelector("#inboxTable tbody");
    tbody.innerHTML = "";

    if (Array.isArray(data) && data.length > 0) {
      data.forEach(msg => {
        const row = document.createElement("tr");
        row.innerHTML = `
          <td>${msg.from || ""}</td>
          <td>${msg.subject || ""}</td>
          <td>${msg.snippet || ""}</td>
        `;
        tbody.appendChild(row);
      });
      showAlert("📥 Inbox carregada com sucesso!", "success");
    } else {
      showAlert("Nenhuma mensagem encontrada.", "warning");
    }
  } catch (err) {
    showAlert("❌ Erro ao buscar inbox: " + err, "danger");
  }
});

// Enviar email
document.getElementById("sendForm").addEventListener("submit", async (e) => {
  e.preventDefault();

  const to = document.getElementById("to").value;
  const subject = document.getElementById("subject").value;
  const body = document.getElementById("body").value;

  try {
    const res = await fetch(`${BASE_URL}/gmail/send`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ to, subject, body })
    });

    const result = await res.json();

    if (result.status === "success") {
      showAlert(result.message, "success");
      document.getElementById("sendForm").reset();
    } else {
      showAlert(result.error || "Erro desconhecido ao enviar email.", "danger");
    }
  } catch (err) {
    showAlert("❌ Erro ao enviar email: " + err, "danger");
  }
});
