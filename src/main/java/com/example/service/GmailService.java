package com.example.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import org.springframework.stereotype.Service;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.Base64;
import java.util.Properties;

@Service
public class GmailService {

    // 👉 Aqui você injeta o token manualmente (pode vir de config, banco ou callback OAuth2)
    private final String accessToken;

    public GmailService() {
        // Para testes, você pode colocar um token fixo aqui
        // Em produção, injete dinamicamente após o login OAuth2
        this.accessToken = "SEU_ACCESS_TOKEN_AQUI";
    }

    private Gmail getGmailClient() throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        HttpRequestInitializer requestInitializer = request -> {
            request.getHeaders().setAuthorization("Bearer " + accessToken);
        };

        return new Gmail.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName("gmail-controller")
                .build();
    }

    // Listar apenas o total de mensagens
    public String listMessages() throws Exception {
        Gmail gmail = getGmailClient();
        List<Message> messages = gmail.users().messages().list("me").execute().getMessages();

        if (messages == null || messages.isEmpty()) {
            return "Nenhuma mensagem encontrada.";
        }
        return "Total de mensagens: " + messages.size();
    }

    // Listar mensagens detalhadas (remetente, assunto, snippet)
    public List<Map<String, String>> listMessagesDetailed() throws Exception {
        Gmail gmail = getGmailClient();
        List<Message> messages = gmail.users().messages().list("me")
                .setMaxResults(10L)
                .execute()
                .getMessages();

        List<Map<String, String>> result = new ArrayList<>();

        if (messages != null) {
            for (Message m : messages) {
                Message fullMessage = gmail.users().messages().get("me", m.getId()).execute();

                String snippet = fullMessage.getSnippet();
                String subject = "";
                String from = "";

                if (fullMessage.getPayload() != null && fullMessage.getPayload().getHeaders() != null) {
                    for (var header : fullMessage.getPayload().getHeaders()) {
                        if ("Subject".equalsIgnoreCase(header.getName())) {
                            subject = header.getValue();
                        }
                        if ("From".equalsIgnoreCase(header.getName())) {
                            from = header.getValue();
                        }
                    }
                }

                result.add(Map.of(
                        "id", m.getId(),
                        "from", from,
                        "subject", subject,
                        "snippet", snippet
                ));
            }
        }

        return result;
    }

    // Enviar email dinâmico
    public void sendEmail(String to, String subject, String body) throws Exception {
        Gmail gmail = getGmailClient();

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress("me")); // "me" usa a conta autenticada
        email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
        email.setText(body);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

        Message gmailMessage = new Message();
        gmailMessage.setRaw(encodedEmail);

        gmail.users().messages().send("me", gmailMessage).execute();
    }
}
