package com.codelry.demo.sessionapi;

import com.codelry.demo.sessionapi.model.Session;
import com.codelry.demo.sessionapi.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

@SpringBootTest
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = SessionControllerTest.Initializer.class)
class SessionControllerTest {

    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:latest"));

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
      @Override
      public void initialize(ConfigurableApplicationContext applicationContext) {
        redis.withExposedPorts(6379);
        redis.start();
        TestPropertyValues.of(
            "spring.data.redis.host=" + redis.getHost(),
            "spring.data.redis.port=" + redis.getMappedPort(6379)
        ).applyTo(applicationContext.getEnvironment());
      }
    }

    @AfterAll
    static void afterAll() {
      redis.stop();
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        cleanupTestData();
    }

    @Test
    void createSession_ShouldReturnCreatedWithSessionId() {
        webTestClient.post().uri("/v1/api/session")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.sessionId").exists();
    }

    @Test
    void getSession_WithValidUUID_ShouldReturnSession() {
        Session createdSession = sessionService.createSession().block();
        assert createdSession != null;
        UUID sessionId = createdSession.getSessionId();

        webTestClient.get().uri("/v1/api/session/" + sessionId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.sessionId").isEqualTo(sessionId.toString())
            .jsonPath("$.createdAt").exists()
            .jsonPath("$.lastAccessedAt").exists();
    }

    @Test
    void getSession_WithInvalidUUID_ShouldReturnBadRequest() {
        webTestClient.get().uri("/v1/api/session/invalid-uuid")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void getSession_WithNonExistentSession_ShouldReturnNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        webTestClient.get().uri("/v1/api/session/" + nonExistentId)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void sessionLifecycle_CreateAndRetrieve() throws Exception {
        byte[] createResponse = webTestClient.post().uri("/v1/api/session")
            .exchange()
            .expectStatus().isCreated()
            .expectBody().jsonPath("$.sessionId").exists()
            .returnResult()
            .getResponseBodyContent();

        String sessionId = objectMapper.readTree(createResponse).get("sessionId").asText();

        webTestClient.get().uri("/v1/api/session/" + sessionId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.sessionId").isEqualTo(sessionId);
    }

    @Test
    void multipleSessions_ShouldCreateIndependentSessions() throws Exception {
        byte[] firstResponse = webTestClient.post().uri("/v1/api/session")
            .exchange()
            .expectStatus().isCreated()
            .returnResult()
            .getResponseBodyContent();

        byte[] secondResponse = webTestClient.post().uri("/v1/api/session")
            .exchange()
            .expectStatus().isCreated()
            .returnResult()
            .getResponseBodyContent();

        String firstSessionId = objectMapper.readTree(firstResponse).get("sessionId").asText();
        String secondSessionId = objectMapper.readTree(secondResponse).get("sessionId").asText();

        assert !firstSessionId.equals(secondSessionId);

        webTestClient.get().uri("/v1/api/session/" + firstSessionId)
            .exchange()
            .expectStatus().isOk();

        webTestClient.get().uri("/v1/api/session/" + secondSessionId)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void getSession_ShouldUpdateLastAccessedTime() throws Exception {
        Session createdSession = sessionService.createSession().block();
        assert createdSession != null;
        UUID sessionId = createdSession.getSessionId();

        Thread.sleep(100);

        byte[] response = webTestClient.get().uri("/v1/api/session/" + sessionId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .returnResult()
            .getResponseBodyContent();

        var sessionNode = objectMapper.readTree(response);
        String lastAccessedAt = sessionNode.get("lastAccessedAt").asText();
        String createdAt = sessionNode.get("createdAt").asText();

        assert !lastAccessedAt.equals(createdAt);
    }

    private void cleanupTestData() {
        try {
            Assertions.assertNotNull(redisTemplate.getConnectionFactory());
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        } catch (Exception e) {
            System.out.println("Warning: Could not clean up test data: " + e.getMessage());
        }
    }
}
