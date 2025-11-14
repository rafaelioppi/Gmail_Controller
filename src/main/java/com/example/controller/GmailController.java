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

    // Endpoint para listar mensagens detalhadas em JSON
    @GetMapping("/inbox")
    public ResponseEntity<List<Map<String, String>>> listMessages() {
        try {
            List<Map<String, String>> messages = gmailService.listMessagesDetailed();
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of(Map.of("error", e.getMessage())));
        }
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
}

    // Endpoint para enviar email via POST JSON
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendEmail(@RequestBody Map<String, String> payload) {
        try {
            String to = payload.get("to");
            String subject = payload.get("subject");
            String body = payload.get("body");

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
}
