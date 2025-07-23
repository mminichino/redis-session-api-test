package com.codelry.demo.sessionapi.controller;

import com.codelry.demo.sessionapi.model.Session;
import com.codelry.demo.sessionapi.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/session")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);
    
    private final SessionService sessionService;

    @Autowired
    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createSession() {
        Session session = sessionService.createSession();
        Map<String, String> response = Map.of("sessionId", session.getSessionId().toString());
        logger.info("Successfully created session: {}", session.getSessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Session> getSession(@PathVariable String sessionId) {
        try {
            UUID uuid = UUID.fromString(sessionId);
            Optional<Session> session = sessionService.getSession(uuid);
            
            if (session.isPresent()) {
                logger.info("Successfully retrieved session: {}", sessionId);
                return ResponseEntity.ok(session.get());
            } else {
                logger.debug("Session not found: {}", sessionId);
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format provided: {}", sessionId);
            throw e;
        }
    }
}
