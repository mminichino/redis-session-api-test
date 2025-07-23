package com.codelry.demo.sessionapi.service;

import com.codelry.demo.sessionapi.model.Session;
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

    @Autowired
    public SessionService(RedisTemplate<String, String> redisTemplate,
                         RetryTemplate retryTemplate) {
        this.redisTemplate = redisTemplate;
        this.retryTemplate = retryTemplate;
    }

    public Session createSession() {
        return retryTemplate.execute(context -> {
            logger.debug("Attempting to create session in Redis (attempt {})", context.getRetryCount() + 1);
            Session session = new Session();
            String key = SESSION_KEY_PREFIX + session.getSessionId().toString();

            Map<String, String> sessionHash = new HashMap<>();
            sessionHash.put(FIELD_SESSION_ID, session.getSessionId().toString());
            sessionHash.put(FIELD_CREATED_AT, session.getCreatedAt().format(DATE_FORMATTER));
            sessionHash.put(FIELD_LAST_ACCESSED_AT, session.getLastAccessedAt().format(DATE_FORMATTER));

            redisTemplate.opsForHash().putAll(key, sessionHash);
            redisTemplate.expire(key, SESSION_EXPIRATION_HOURS, TimeUnit.HOURS);

            logger.info("Successfully created session with ID: {}", session.getSessionId());
            return session;
        }, context -> {
            logger.error("Failed to create session after all retry attempts", context.getLastThrowable());
            throw new RuntimeException("Unable to create session - Redis service unavailable", context.getLastThrowable());
        });
    }

    public Optional<Session> getSession(UUID sessionId) {
        return retryTemplate.execute(context -> {
            logger.debug("Attempting to retrieve session {} from Redis (attempt {})", sessionId, context.getRetryCount() + 1);
            String key = SESSION_KEY_PREFIX + sessionId.toString();
            
            try {
              Map<Object, Object> hashEntries = redisTemplate.opsForHash().entries(key);

              if (hashEntries.isEmpty()) {
                logger.debug("Session {} not found in Redis", sessionId);
                return Optional.empty();
              }

              Session session = convertHashToSession(hashEntries);

              LocalDateTime now = LocalDateTime.now();
              session.setLastAccessedAt(now);

              redisTemplate.opsForHash().put(key, FIELD_LAST_ACCESSED_AT, now.format(DATE_FORMATTER));
              redisTemplate.expire(key, SESSION_EXPIRATION_HOURS, TimeUnit.HOURS);

              logger.info("Successfully retrieved and updated session {}", sessionId);
              return Optional.of(session);
            } catch (Exception e) {
                logger.error("Failed to retrieve session {}", sessionId, e);
                throw new RuntimeException("Failed to retrieve session", e);
            }
        }, context -> {
            logger.error("Failed to retrieve session {} after all retry attempts", sessionId, context.getLastThrowable());
            throw new RuntimeException("Unable to retrieve session - Redis service unavailable", context.getLastThrowable());
        });
    }

    public boolean sessionExists(UUID sessionId) {
        return retryTemplate.execute(context -> {
            logger.debug("Checking if session {} exists in Redis (attempt {})", sessionId, context.getRetryCount() + 1);
            String key = SESSION_KEY_PREFIX + sessionId.toString();
          return redisTemplate.hasKey(key);
        }, context -> {
            logger.error("Failed to check session existence {} after all retry attempts", sessionId, context.getLastThrowable());
            throw new RuntimeException("Unable to check session existence - Redis service unavailable", context.getLastThrowable());
        });
    }

    public int getSessionCount() {
        return retryTemplate.execute(context -> {
            logger.debug("Retrieving session count from Redis (attempt {})", context.getRetryCount() + 1);
            Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
            int count = keys.size();
            logger.debug("Current session count: {}", count);
            return count;
        }, context -> {
            logger.error("Failed to get session count after all retry attempts", context.getLastThrowable());
            throw new RuntimeException("Unable to get session count - Redis service unavailable", context.getLastThrowable());
        });
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
