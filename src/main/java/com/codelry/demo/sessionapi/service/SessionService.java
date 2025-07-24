package com.codelry.demo.sessionapi.service;

import com.codelry.demo.sessionapi.model.Session;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class SessionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final int SESSION_EXPIRATION_HOURS = 24;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String FIELD_SESSION_ID = "sessionId";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_ACCESSED_AT = "lastAccessedAt";

    private final RedisTemplate<String, String> redisTemplate;
    private final RetryTemplate retryTemplate;
    private final MeterRegistry meterRegistry;

    private final Timer createSessionTimer;
    private final Timer getSessionTimer;
    private final Timer sessionExistsTimer;
    private final Timer getSessionCountTimer;

    @Autowired
    public SessionService(RedisTemplate<String, String> redisTemplate,
                         RetryTemplate retryTemplate,
                         MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.retryTemplate = retryTemplate;
        this.meterRegistry = meterRegistry;

        this.createSessionTimer = Timer.builder("session.operation.duration")
                .description("Time taken to create a session")
                .tag("operation", "create")
                .register(meterRegistry);
                
        this.getSessionTimer = Timer.builder("session.operation.duration")
                .description("Time taken to get a session")
                .tag("operation", "get")
                .register(meterRegistry);
                
        this.sessionExistsTimer = Timer.builder("session.operation.duration")
                .description("Time taken to check session existence")
                .tag("operation", "exists")
                .register(meterRegistry);
                
        this.getSessionCountTimer = Timer.builder("session.operation.duration")
                .description("Time taken to get session count")
                .tag("operation", "count")
                .register(meterRegistry);
    }

    public Session createSession() {
        try {
            return createSessionTimer.recordCallable(() -> {
                Timer.Sample retryTimerSample = Timer.start(meterRegistry);
                
                return retryTemplate.execute(context -> {
                    logger.debug("Attempting to create session in Redis (attempt {})", context.getRetryCount() + 1);

                    meterRegistry.counter("session.operation.retries", 
                        Tags.of("operation", "create", "attempt", String.valueOf(context.getRetryCount() + 1)))
                        .increment();
                    
                    Session session = new Session();
                    String key = SESSION_KEY_PREFIX + session.getSessionId().toString();

                    Map<String, String> sessionHash = new HashMap<>();
                    sessionHash.put(FIELD_SESSION_ID, session.getSessionId().toString());
                    sessionHash.put(FIELD_CREATED_AT, session.getCreatedAt().format(DATE_FORMATTER));
                    sessionHash.put(FIELD_LAST_ACCESSED_AT, session.getLastAccessedAt().format(DATE_FORMATTER));

                    redisTemplate.opsForHash().putAll(key, sessionHash);
                    redisTemplate.expire(key, SESSION_EXPIRATION_HOURS, TimeUnit.HOURS);

                    logger.info("Successfully created session with ID: {}", session.getSessionId());

                    meterRegistry.counter("session.operation.success", Tags.of("operation", "create")).increment();
                    
                    return session;
                }, context -> {
                    logger.error("Failed to create session after all retry attempts", context.getLastThrowable());

                    retryTimerSample.stop(Timer.builder("session.operation.retry.total.duration")
                        .description("Total time including all retry attempts")
                        .tag("operation", "create")
                        .register(meterRegistry));

                    meterRegistry.counter("session.operation.failure", Tags.of("operation", "create")).increment();
                    
                    throw new RuntimeException("Unable to create session - Redis service unavailable", context.getLastThrowable());
                });
            });
        } catch (Exception e) {
            logger.error("Error recording metrics for createSession", e);
            throw new RuntimeException("Error recording metrics for createSession", e);
        }
    }

    public Optional<Session> getSession(UUID sessionId) {
        try {
            return getSessionTimer.recordCallable(() -> {
                Timer.Sample retryTimerSample = Timer.start(meterRegistry);
                
                return retryTemplate.execute(context -> {
                    logger.debug("Attempting to retrieve session {} from Redis (attempt {})", sessionId, context.getRetryCount() + 1);

                    meterRegistry.counter("session.operation.retries", 
                        Tags.of("operation", "get", "attempt", String.valueOf(context.getRetryCount() + 1)))
                        .increment();
                    
                    String key = SESSION_KEY_PREFIX + sessionId.toString();
                    
                    try {
                        Map<Object, Object> hashEntries = redisTemplate.opsForHash().entries(key);

                        if (hashEntries.isEmpty()) {
                            logger.debug("Session {} not found in Redis", sessionId);
                            meterRegistry.counter("session.operation.not_found", Tags.of("operation", "get")).increment();
                            return Optional.empty();
                        }

                        Session session = convertHashToSession(hashEntries);

                        LocalDateTime now = LocalDateTime.now();
                        session.setLastAccessedAt(now);

                        redisTemplate.opsForHash().put(key, FIELD_LAST_ACCESSED_AT, now.format(DATE_FORMATTER));
                        redisTemplate.expire(key, SESSION_EXPIRATION_HOURS, TimeUnit.HOURS);

                        logger.info("Successfully retrieved and updated session {}", sessionId);

                        meterRegistry.counter("session.operation.success", Tags.of("operation", "get")).increment();
                        
                        return Optional.of(session);
                    } catch (Exception e) {
                        logger.error("Failed to retrieve session {}", sessionId, e);
                        throw new RuntimeException("Failed to retrieve session", e);
                    }
                }, context -> {
                    logger.error("Failed to retrieve session {} after all retry attempts", sessionId, context.getLastThrowable());

                    retryTimerSample.stop(Timer.builder("session.operation.retry.total.duration")
                        .description("Total time including all retry attempts")
                        .tag("operation", "get")
                        .register(meterRegistry));

                    meterRegistry.counter("session.operation.failure", Tags.of("operation", "get")).increment();
                    
                    throw new RuntimeException("Unable to retrieve session - Redis service unavailable", context.getLastThrowable());
                });
            });
        } catch (Exception e) {
            logger.error("Error recording metrics for getSession", e);
            throw new RuntimeException("Error recording metrics for getSession", e);
        }
    }

    public boolean sessionExists(UUID sessionId) {
        try {
          return Boolean.TRUE.equals(sessionExistsTimer.recordCallable(() -> {
            Timer.Sample retryTimerSample = Timer.start(meterRegistry);

            return retryTemplate.execute(context -> {
              logger.debug("Checking if session {} exists in Redis (attempt {})", sessionId, context.getRetryCount() + 1);

              meterRegistry.counter("session.operation.retries",
                      Tags.of("operation", "exists", "attempt", String.valueOf(context.getRetryCount() + 1)))
                  .increment();

              String key = SESSION_KEY_PREFIX + sessionId.toString();
              boolean exists = redisTemplate.hasKey(key);

              meterRegistry.counter("session.operation.success", Tags.of("operation", "exists")).increment();

              return exists;
            }, context -> {
              logger.error("Failed to check session existence {} after all retry attempts", sessionId, context.getLastThrowable());

              retryTimerSample.stop(Timer.builder("session.operation.retry.total.duration")
                  .description("Total time including all retry attempts")
                  .tag("operation", "exists")
                  .register(meterRegistry));

              meterRegistry.counter("session.operation.failure", Tags.of("operation", "exists")).increment();

              throw new RuntimeException("Unable to check session existence - Redis service unavailable", context.getLastThrowable());
            });
          }));
        } catch (Exception e) {
            logger.error("Error recording metrics for sessionExists", e);
            throw new RuntimeException("Failed to check session existence", e);
        }
    }

    public int getSessionCount() {
        try {
        Integer result = getSessionCountTimer.recordCallable(() -> {
            Timer.Sample retryTimerSample = Timer.start(meterRegistry);
            
            return retryTemplate.execute(context -> {
                logger.debug("Retrieving session count from Redis (attempt {})", context.getRetryCount() + 1);

                meterRegistry.counter("session.operation.retries", 
                    Tags.of("operation", "count", "attempt", String.valueOf(context.getRetryCount() + 1)))
                    .increment();
                
                Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
                int count = keys.size();
                logger.debug("Current session count: {}", count);

                meterRegistry.counter("session.operation.success", Tags.of("operation", "count")).increment();
                meterRegistry.gauge("session.total.count", count);
                
                return count;
            }, context -> {
                logger.error("Failed to get session count after all retry attempts", context.getLastThrowable());

                retryTimerSample.stop(Timer.builder("session.operation.retry.total.duration")
                    .description("Total time including all retry attempts")
                    .tag("operation", "count")
                    .register(meterRegistry));

                meterRegistry.counter("session.operation.failure", Tags.of("operation", "count")).increment();
                
                throw new RuntimeException("Unable to get session count - Redis service unavailable", context.getLastThrowable());
            });
        });
        
        return result != null ? result : 0;
    } catch (Exception e) {
        logger.error("Error recording metrics for getSessionCount", e);
        throw new RuntimeException("Failed to get session count", e);
    }
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
