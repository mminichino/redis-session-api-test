package com.codelry.demo.sessionapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorResourceFactory;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

import java.time.Duration;

@Configuration
public class NettyConfig {

  @Bean
  public ReactorResourceFactory reactorResourceFactory() {
    ReactorResourceFactory factory = new ReactorResourceFactory();
    factory.setUseGlobalResources(true);
    factory.setLoopResources(LoopResources.create("http-nio", 4, 16, true));
    ConnectionProvider connectionProvider = ConnectionProvider.builder("connection-pool")
        .maxConnections(512)
        .pendingAcquireTimeout(Duration.ofMillis(10000))
        .build();
    factory.setConnectionProvider(connectionProvider);
    return factory;
  }
}
