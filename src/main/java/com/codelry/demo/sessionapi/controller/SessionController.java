package com.codelry.demo.sessionapi.controller;

import com.codelry.demo.sessionapi.model.Session;
import com.codelry.demo.sessionapi.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
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
    public Mono<ResponseEntity<Map<String, String>>> createSession() {
        return sessionService.createSession()
            .map(session -> {
                Map<String, String> response = Map.of("sessionId", session.getSessionId().toString());
                logger.info("Successfully created session: {}", session.getSessionId());
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            });
    }

    @GetMapping("/{sessionId}")
    public Mono<ResponseEntity<Session>> getSession(@PathVariable String sessionId) {
        try {
            UUID uuid = UUID.fromString(sessionId);
            return sessionService.getSession(uuid)
                .map(session -> {
                    logger.info("Successfully retrieved session: {}", sessionId);
                    return ResponseEntity.ok(session);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format provided: {}", sessionId);
            throw e;
        }
    }
}
