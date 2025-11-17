package com.example.controller;

import com.example.service.GmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gmail")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    /**
     * Lista mensagens detalhadas da caixa de entrada do Gmail.
     */
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

    /**
     * Endpoint simples para teste de disponibilidade.
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    /**
     * Envia um email usando o Gmail API.
     * Espera no corpo da requisição os campos: to, subject e body.
     */
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

    /**
     * Retorna detalhes completos de uma mensagem pelo ID.
     */
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
     * Apaga uma mensagem pelo ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable("id") String id) {
        try {
            gmailService.deleteMessageById(id);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "🗑️ Email apagado com sucesso!"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
