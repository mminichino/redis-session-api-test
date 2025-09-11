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

    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis/redis-stack:latest"))
        .withExposedPorts(6379)
        .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(
            new com.github.dockerjava.api.model.HostConfig().withPortBindings(
                new com.github.dockerjava.api.model.PortBinding(
                    com.github.dockerjava.api.model.Ports.Binding.bindPort(16379),
                    new com.github.dockerjava.api.model.ExposedPort(6379)
                )
            )
        ));

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
      @Override
      public void initialize(ConfigurableApplicationContext applicationContext) {
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
