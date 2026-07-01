package com.school.controller;

import com.school.service.TwilioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*") // Allows calls from local frontend server
public class MessageController {

    private final TwilioService twilioService;

    @Autowired
    public MessageController(TwilioService twilioService) {
        this.twilioService = twilioService;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        String message = request.get("message");

        if (phone == null || phone.trim().isEmpty() || message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phone and message are required."));
        }

        boolean success = twilioService.sendMessage(phone, message);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "SMS simulated successfully via Twilio placeholder."));
        } else {
            return ResponseEntity.internalServerError().body(Map.of("status", "failed", "message", "Failed to send message."));
        }
    }
}
