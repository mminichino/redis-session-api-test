package com.codelry.demo.sessionapi.service;

import com.codelry.demo.sessionapi.model.Session;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SessionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final int SESSION_EXPIRATION_HOURS = 24;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String FIELD_SESSION_ID = "sessionId";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_ACCESSED_AT = "lastAccessedAt";

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${app.retry.max-retries:5}")
    private int maxRetries;

    @Value("${app.retry.delay:1000}")
    private long retryDelayMillis;

    @Autowired
    public SessionService(ReactiveRedisTemplate<String, String> reactiveRedisTemplate, MeterRegistry meterRegistry) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.meterRegistry = meterRegistry;
    }

    public Mono<Session> createSession() {
        logger.debug("Attempting to create session in Redis");

        Session session = new Session();
        String key = SESSION_KEY_PREFIX + session.getSessionId().toString();

        Map<String, String> sessionHash = new HashMap<>();
        sessionHash.put(FIELD_SESSION_ID, session.getSessionId().toString());
        sessionHash.put(FIELD_CREATED_AT, session.getCreatedAt().format(DATE_FORMATTER));
        sessionHash.put(FIELD_LAST_ACCESSED_AT, session.getLastAccessedAt().format(DATE_FORMATTER));

        Timer.Sample sample = Timer.start(meterRegistry);
        return reactiveRedisTemplate.opsForHash().putAll(key, sessionHash)
            .then(reactiveRedisTemplate.expire(key, Duration.ofHours(SESSION_EXPIRATION_HOURS)))
            .thenReturn(session)
            .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(retryDelayMillis))
                .jitter(0.75)
                .filter(throwable -> !(throwable instanceof IllegalArgumentException))
                .doBeforeRetry(retrySignal -> logger.warn("Retrying createSession after error: {}. Retry count: {}",
                    retrySignal.failure().getMessage(), retrySignal.totalRetries() + 1)))
            .doFinally(signalType -> sample.stop(meterRegistry.timer("session.operation.duration",
                "operation", "create", "status", signalType.toString())))
            .doOnSuccess(s -> logger.info("Successfully created session with ID: {}", session.getSessionId()))
            .doOnError(e -> logger.error("Error creating session in Redis", e));
    }

    public Mono<Session> getSession(UUID sessionId) {
        logger.debug("Attempting to retrieve session {} from Redis", sessionId);
        String key = SESSION_KEY_PREFIX + sessionId.toString();

        return reactiveRedisTemplate.opsForHash().entries(key)
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .flatMap(entries -> {
                if (entries.isEmpty()) {
                    logger.debug("Session {} not found in Redis", sessionId);
                    return Mono.empty();
                }

                Session session = convertHashToSession(entries);
                LocalDateTime now = LocalDateTime.now();
                session.setLastAccessedAt(now);

                return reactiveRedisTemplate.opsForHash().put(key, FIELD_LAST_ACCESSED_AT, session.getLastAccessedAt().format(DATE_FORMATTER))
                    .then(reactiveRedisTemplate.expire(key, Duration.ofHours(SESSION_EXPIRATION_HOURS)))
                    .thenReturn(session)
                    .doOnSuccess(s -> logger.info("Successfully retrieved and updated session {}", sessionId));
            })
            .doOnError(e -> logger.error("Failed to retrieve session {}", sessionId, e));
    }

    public Mono<Boolean> sessionExists(UUID sessionId) {
        logger.debug("Checking if session {} exists in Redis", sessionId);
        String key = SESSION_KEY_PREFIX + sessionId.toString();
        return reactiveRedisTemplate.hasKey(key);
    }

    public Mono<Integer> getSessionCount() {
        logger.debug("Retrieving session count from Redis");
        return reactiveRedisTemplate.keys(SESSION_KEY_PREFIX + "*")
            .collectList()
            .map(List::size)
            .doOnSuccess(s -> logger.debug("Retrieved session count: {}", s));
    }

    private Session convertHashToSession(Map<Object, Object> hashEntries) {
        String sessionIdStr = (String) hashEntries.get(FIELD_SESSION_ID);
        String createdAtStr = (String) hashEntries.get(FIELD_CREATED_AT);
        String lastAccessedAtStr = (String) hashEntries.get(FIELD_LAST_ACCESSED_AT);

        UUID sessionId = UUID.fromString(sessionIdStr);
        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr, DATE_FORMATTER);
        LocalDateTime lastAccessedAt = LocalDateTime.parse(lastAccessedAtStr, DATE_FORMATTER);

        Session session = new Session(sessionId);
        session.setCreatedAt(createdAt);
        session.setLastAccessedAt(lastAccessedAt);

        return session;
    }
}
