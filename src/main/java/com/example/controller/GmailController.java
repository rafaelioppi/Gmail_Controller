package com.example.controller;

import com.example.service.GmailService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gmail")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    @GetMapping("/inbox")
    public ResponseEntity<?> listMessages() {
        try {
            List<Map<String, String>> messages = gmailService.listMessagesDetailed();
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendEmail(@RequestBody Map<String, String> payload) {
        try {
            String to = payload.get("to");
            String subject = payload.get("subject");
            String body = payload.get("body");

            if (to == null || subject == null || body == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Campos 'to', 'subject' e 'body' são obrigatórios."));
            }

            gmailService.sendEmail(to, subject, body);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "✅ Email enviado para " + to
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMessage(@PathVariable("id") String id) {
        try {
            Map<String, Object> message = gmailService.getMessageById(id);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Endpoint para exclusão permanente. O Service executa: Lixeira -> Esvaziar Lixeira.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable("id") String id) {
        try {
            // CHAMADA CORRETA: Usa o método que implementa a sequência de exclusão (Lixeira + Esvaziar).
            gmailService.trashMessageThenEmpty(id); 
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "🗑️ Email apagado permanentemente com sucesso!"
            ));
        } catch (IllegalStateException e) {
            // Captura 403 Forbidden: Geralmente ocorre quando falta o escopo 'gmail.modify'.
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (GoogleJsonResponseException e) {
            // Captura erros específicos da API do Google (ex: 404 Not Found).
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Mensagem não encontrada."));
            }
            // Trata outros erros da API do Google.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // Captura qualquer outro erro inesperado.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}