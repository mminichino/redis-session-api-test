package com.codelry.demo.sessionapi;

import com.codelry.demo.sessionapi.model.Session;
import com.codelry.demo.sessionapi.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class SessionServiceRedisTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private SessionService sessionService;

    @Test
    void sessionLifecycle_ShouldWorkWithRealRedis() {
        Session session = sessionService.createSession();
        assertNotNull(session.getSessionId());

        Optional<Session> retrieved = sessionService.getSession(session.getSessionId());
        assertTrue(retrieved.isPresent());
        assertEquals(session.getSessionId(), retrieved.get().getSessionId());

        assertTrue(sessionService.sessionExists(session.getSessionId()));

        int count = sessionService.getSessionCount();
        assertTrue(count >= 1);
    }
}
