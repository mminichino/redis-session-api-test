package com.codelry.demo.sessionapi.service;

import com.codelry.demo.sessionapi.model.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SessionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final int SESSION_EXPIRATION_HOURS = 24;
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;

    @Autowired
    public SessionService(RedisTemplate<String, String> redisTemplate, 
                         ObjectMapper objectMapper, 
                         RetryTemplate retryTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
    }

    public Session createSession() {
        return retryTemplate.execute(context -> {
            logger.debug("Attempting to create session in Redis (attempt {})", context.getRetryCount() + 1);
            Session session = new Session();
            String key = SESSION_KEY_PREFIX + session.getSessionId().toString();
            
            try {
                String sessionJson = objectMapper.writeValueAsString(session);
                redisTemplate.opsForValue().set(key, sessionJson, SESSION_EXPIRATION_HOURS, TimeUnit.HOURS);
                logger.info("Successfully created session with ID: {}", session.getSessionId());
                return session;
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize session", e);
                throw new RuntimeException("Failed to serialize session", e);
            }
        }, context -> {
            logger.error("Failed to create session after all retry attempts", context.getLastThrowable());
            throw new RuntimeException("Unable to create session - Redis service unavailable", context.getLastThrowable());
        });
    }

    public Optional<Session> getSession(UUID sessionId) {
        return retryTemplate.execute(context -> {
            logger.debug("Attempting to retrieve session {} from Redis (attempt {})", sessionId, context.getRetryCount() + 1);
            String key = SESSION_KEY_PREFIX + sessionId.toString();
            String sessionJson = redisTemplate.opsForValue().get(key);
            
            if (sessionJson == null) {
                logger.debug("Session {} not found in Redis", sessionId);
                return Optional.empty();
            }
            
            try {
                Session session = objectMapper.readValue(sessionJson, Session.class);
                session.updateLastAccessed();
                
                // Update the session in Redis with the new last accessed time
                String updatedSessionJson = objectMapper.writeValueAsString(session);
                redisTemplate.opsForValue().set(key, updatedSessionJson, SESSION_EXPIRATION_HOURS, TimeUnit.HOURS);
                
                logger.info("Successfully retrieved and updated session {}", sessionId);
                return Optional.of(session);
            } catch (JsonProcessingException e) {
                logger.error("Failed to deserialize session {}", sessionId, e);
                throw new RuntimeException("Failed to deserialize session", e);
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
}
