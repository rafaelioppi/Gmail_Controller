package com.example.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;

@Service
public class GmailService {

    // Assumimos que o objeto Gmail (o cliente autenticado) é injetado ou acessado
    // via um proxy que representa o usuário atual.
    private final Gmail gmail;
    private static final String USER_ID = "me"; // Representa o usuário logado

    // O construtor injeta o cliente Gmail
    public GmailService(Gmail gmail) {
        this.gmail = gmail;
    }

    /**
     * Constrói e envia um email.
     * @param to Destinatário.
     * @param subject Assunto.
     * @param body Corpo do email.
     * @throws MessagingException, IOException
     */
    public void sendEmail(String to, String subject, String body) throws MessagingException, IOException {
        MimeMessage email = createEmail(to, USER_ID, subject, body);
        sendMessage(gmail, USER_ID, email);
    }

    private MimeMessage createEmail(String to, String from, String subject, String body) throws MessagingException {
        MimeMessage email = new MimeMessage(Session.getDefaultInstance(System.getProperties()));
        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
        email.setText(body);
        return email;
    }

    private Message sendMessage(Gmail service, String userId, MimeMessage email) throws IOException, MessagingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        email.writeTo(baos);
        String encodedEmail = Base64.getUrlEncoder().encodeToString(baos.toByteArray());
        Message message = new Message().setRaw(encodedEmail);
        
        // Envia a mensagem
        message = service.users().messages().send(userId, message).execute();
        return message;
    }

    /**
     * Lista mensagens da Inbox (detalhado).
     * @return Lista de mapas com detalhes da mensagem.
     * @throws IOException
     */
    public List<Map<String, String>> listMessagesDetailed() throws IOException {
        ListMessagesResponse response = gmail.users().messages().list(USER_ID).setLabelIds(Collections.singletonList("INBOX")).execute();
        List<Map<String, String>> detailedMessages = new ArrayList<>();
        
        List<Message> messages = response.getMessages();
        if (messages != null) {
            for (Message message : messages) {
                // Busca detalhes de cada mensagem
                Message fullMessage = gmail.users().messages().get(USER_ID, message.getId()).setFormat("metadata").setFields("id,snippet,payload/headers").execute();

                Map<String, String> details = new HashMap<>();
                details.put("id", fullMessage.getId());
                details.put("snippet", fullMessage.getSnippet());
                
                // Extrai Remetente e Assunto dos headers
                if (fullMessage.getPayload() != null && fullMessage.getPayload().getHeaders() != null) {
                    for (MessagePartHeader header : fullMessage.getPayload().getHeaders()) {
                        if ("Subject".equalsIgnoreCase(header.getName())) {
                            details.put("subject", header.getValue());
                        } else if ("From".equalsIgnoreCase(header.getName())) {
                            details.put("from", header.getValue());
                        }
                    }
                }
                detailedMessages.add(details);
            }
        }
        return detailedMessages;
    }

    /**
     * Obtém uma mensagem por ID, incluindo o corpo (raw content).
     * @param messageId ID da mensagem.
     * @return Mapa com detalhes e corpo da mensagem.
     * @throws IOException
     */
    public Map<String, Object> getMessageById(String messageId) throws IOException {
        Message message = gmail.users().messages().get(USER_ID, messageId).setFormat("full").execute();
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", message.getId());
        result.put("snippet", message.getSnippet());

        // Extrai Headers
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if ("Subject".equalsIgnoreCase(header.getName())) {
                    result.put("subject", header.getValue());
                } else if ("From".equalsIgnoreCase(header.getName())) {
                    result.put("from", header.getValue());
                }
            }
        }
        
        // Simples decodificação do corpo (melhoria necessária para corpo complexo HTML/Texto)
        String body = extractBody(message);
        result.put("body", body);
        
        // Placeholder para anexos (implementação completa seria mais complexa)
        result.put("attachments", Collections.emptyList()); 
        
        return result;
    }

    // Função auxiliar para extrair o corpo de forma simplificada
    private String extractBody(Message message) {
        if (message.getPayload() != null && message.getPayload().getParts() != null) {
            for (com.google.api.services.gmail.model.MessagePart part : message.getPayload().getParts()) {
                if ("text/html".equals(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
                    // Retorna a parte HTML decodificada
                    return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                } else if ("text/plain".equals(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
                    // Retorna a parte Text decodificada
                    return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                }
            }
        }
        // Se for um corpo simples sem partes
        if (message.getPayload() != null && message.getPayload().getBody() != null && message.getPayload().getBody().getData() != null) {
             return new String(Base64.getUrlDecoder().decode(message.getPayload().getBody().getData()));
        }
        return null;
    }

    /**
     * ⚠️ O MÉTODO DELETAR PERMANENTEMENTE CORRIGIDO ⚠️
     * * Move uma mensagem para a lixeira e, em seguida, a apaga permanentemente.
     * Esta é a única forma de garantir a exclusão permanente via API.
     * * @param messageId O ID da mensagem a ser permanentemente excluída.
     * @throws IOException
     */
    public void trashMessageThenEmpty(String messageId) throws IOException {
        // 1. Mover para a Lixeira (trash)
        // Isso é tecnicamente um 'modify' no Gmail para adicionar a label TRASH.
        // Tentamos mover para o lixo primeiro.
        try {
             gmail.users().messages().trash(USER_ID, messageId).execute();
        } catch (IOException e) {
            // Se já estiver no lixo ou não for encontrado (404), continuamos para o delete.
            if (!e.getMessage().contains("404")) {
                 throw e;
            }
        }
       
        // 2. Apagar permanentemente (delete)
        // Este comando remove o email de qualquer pasta, incluindo o lixo, permanentemente.
        // O escopo de acesso precisa ser 'gmail.modify' (ou superior) para esta operação.
        gmail.users().messages().delete(USER_ID, messageId).execute();
    }
}