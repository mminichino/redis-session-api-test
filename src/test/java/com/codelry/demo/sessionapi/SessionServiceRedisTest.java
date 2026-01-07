package com.codelry.demo.sessionapi;

import com.codelry.demo.sessionapi.model.Session;
import com.codelry.demo.sessionapi.service.SessionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = SessionServiceRedisTest.Initializer.class)
class SessionServiceRedisTest {

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
    private SessionService sessionService;

    @Test
    void sessionLifecycleTest() {
        Session session = sessionService.createSession().block();
        assert session != null;
        assertNotNull(session.getSessionId());

        Session retrieved = sessionService.getSession(session.getSessionId()).block();
        assert retrieved != null;
        assertEquals(session.getSessionId(), retrieved.getSessionId());

        assertEquals(Boolean.TRUE, sessionService.sessionExists(session.getSessionId()).block());

        Optional<Integer> count = sessionService.getSessionCount().blockOptional();
        assert count.isPresent();
        assertTrue(count.get() >= 1);
    }
}
