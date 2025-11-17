package com.example.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.Base64;

@Service
public class GmailService {

    private final OAuth2AuthorizedClientService clientService;

    public GmailService(OAuth2AuthorizedClientService clientService) {
        this.clientService = clientService;
    }

    private String getAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OAuth2AuthorizedClient client = clientService.loadAuthorizedClient("google", authentication.getName());

        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("Usuário não autenticado ou token inválido.");
        }

        return client.getAccessToken().getTokenValue();
    }

    private Gmail getGmailClient() throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        String accessToken = getAccessToken();

        // ✅ Cria GoogleCredentials a partir do AccessToken
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Gmail.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName("gmail-controller")
                .build();
    }

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

                String snippet = fullMessage.getSnippet() != null ? fullMessage.getSnippet() : "";
                String subject = "";
                String from = "";

                if (fullMessage.getPayload() != null && fullMessage.getPayload().getHeaders() != null) {
                    for (MessagePartHeader header : fullMessage.getPayload().getHeaders()) {
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

    private String extractBody(Message message) {
        if (message.getPayload() == null) return "";

        if (message.getPayload().getBody() != null && message.getPayload().getBody().getData() != null) {
            byte[] bytes = Base64.getUrlDecoder().decode(message.getPayload().getBody().getData());
            return new String(bytes);
        }

        if (message.getPayload().getParts() != null) {
            for (MessagePart part : message.getPayload().getParts()) {
                if ((part.getMimeType().equalsIgnoreCase("text/plain") || part.getMimeType().equalsIgnoreCase("text/html"))
                        && part.getBody() != null && part.getBody().getData() != null) {
                    byte[] bytes = Base64.getUrlDecoder().decode(part.getBody().getData());
                    return new String(bytes);
                }
            }
        }

        return "";
    }

    private List<Map<String, String>> extractAttachments(Gmail gmail, Message message) throws Exception {
        List<Map<String, String>> attachments = new ArrayList<>();

        if (message.getPayload() != null && message.getPayload().getParts() != null) {
            for (MessagePart part : message.getPayload().getParts()) {
                if (part.getFilename() != null && !part.getFilename().isEmpty()) {
                    String attachmentId = part.getBody().getAttachmentId();
                    MessagePartBody attachPart = gmail.users().messages().attachments()
                            .get("me", message.getId(), attachmentId)
                            .execute();

                    byte[] fileBytes = Base64.getUrlDecoder().decode(attachPart.getData());

                    attachments.add(Map.of(
                            "filename", part.getFilename(),
                            "mimeType", part.getMimeType(),
                            "size", String.valueOf(part.getBody().getSize()),
                            "contentBase64", Base64.getEncoder().encodeToString(fileBytes)
                    ));
                }
            }
        }

        return attachments;
    }

    public Map<String, Object> getMessageById(String id) throws Exception {
        Gmail gmail = getGmailClient();
        Message fullMessage = gmail.users().messages().get("me", id).execute();

        String subject = "";
        String from = "";

        if (fullMessage.getPayload() != null && fullMessage.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : fullMessage.getPayload().getHeaders()) {
                if ("Subject".equalsIgnoreCase(header.getName())) {
                    subject = header.getValue();
                }
                if ("From".equalsIgnoreCase(header.getName())) {
                    from = header.getValue();
                }
            }
        }

        String body = extractBody(fullMessage);
        List<Map<String, String>> attachments = extractAttachments(gmail, fullMessage);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("from", from);
        result.put("subject", subject);
        result.put("body", body);
        result.put("attachments", attachments);

        return result;
    }

    public void deleteMessageById(String id) throws Exception {
        Gmail gmail = getGmailClient();
        try {
            gmail.users().messages().delete("me", id).execute();
        } catch (Exception e) {
            System.err.println("Erro ao apagar mensagem ID=" + id + ": " + e.getMessage());
            throw new IllegalStateException("Erro ao apagar email: " + e.getMessage(), e);
        }
    }

    public void sendEmail(String to, String subject, String body) throws Exception {
        Gmail gmail = getGmailClient();

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress("no-reply@gmail.com"));
        email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
        email.setText(body);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);

        String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());

        Message gmailMessage = new Message();
        gmailMessage.setRaw(encodedEmail);

        gmail.users().messages().send("me", gmailMessage).execute();
    }
}
