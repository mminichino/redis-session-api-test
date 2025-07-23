package com.codelry.demo.sessionapi;

import com.codelry.demo.sessionapi.model.Session;
import com.codelry.demo.sessionapi.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.data.redis.host=${REDIS_HOST:localhost}",
    "spring.data.redis.port=${REDIS_PORT:12000}",
    "spring.data.redis.password=${REDIS_PASSWORD:}",
    "spring.data.redis.database=${REDIS_DATABASE:0}",
    "spring.data.redis.timeout=2000ms",
    "app.retry.max-attempts=3",
    "app.retry.delay=100",
    "app.retry.multiplier=1.5",
    "logging.level.com.codelry.demo=DEBUG"
})
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanupTestData();
    }

    @Test
    void createSession_ShouldReturnCreatedWithSessionId() throws Exception {
        mockMvc.perform(post("/v1/api/session"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").exists());
    }

    @Test
    void getSession_WithValidUUID_ShouldReturnSession() throws Exception {
        Session createdSession = sessionService.createSession();
        UUID sessionId = createdSession.getSessionId();

        mockMvc.perform(get("/v1/api/session/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.lastAccessedAt").exists());
    }

    @Test
    void getSession_WithInvalidUUID_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/v1/api/session/invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void getSession_WithNonExistentSession_ShouldReturnNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/v1/api/session/" + nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void sessionLifecycle_CreateAndRetrieve() throws Exception {
        String createResponse = mockMvc.perform(post("/v1/api/session"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = objectMapper.readTree(createResponse).get("sessionId").asText();

        mockMvc.perform(get("/v1/api/session/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId));
    }

    @Test
    void multipleSessions_ShouldCreateIndependentSessions() throws Exception {
        String firstResponse = mockMvc.perform(post("/v1/api/session"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(post("/v1/api/session"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstSessionId = objectMapper.readTree(firstResponse).get("sessionId").asText();
        String secondSessionId = objectMapper.readTree(secondResponse).get("sessionId").asText();

        assert !firstSessionId.equals(secondSessionId);

        mockMvc.perform(get("/v1/api/session/" + firstSessionId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/api/session/" + secondSessionId))
                .andExpect(status().isOk());
    }

    @Test
    void getSession_ShouldUpdateLastAccessedTime() throws Exception {
        Session createdSession = sessionService.createSession();
        UUID sessionId = createdSession.getSessionId();

        Thread.sleep(100);

        String response = mockMvc.perform(get("/v1/api/session/" + sessionId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var sessionNode = objectMapper.readTree(response);
        String lastAccessedAt = sessionNode.get("lastAccessedAt").asText();
        String createdAt = sessionNode.get("createdAt").asText();

        assert !lastAccessedAt.equals(createdAt);
    }

    private void cleanupTestData() {
        try {
            Set<String> keys = redisTemplate.keys("session:*");
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            System.out.println("Warning: Could not clean up test data: " + e.getMessage());
        }
    }
}
