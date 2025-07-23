package com.codelry.demo.sessionapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class RedisConfig {

    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.data.redis.host:localhost}")
      private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.ssl:false}")
    private boolean useSsl;

    @Value("${spring.data.redis.timeout:5000ms}")
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration timeout;

    @Value("${spring.data.redis.jedis.pool.max-active:20}")
    private int maxActive;

    @Value("${spring.data.redis.jedis.pool.max-idle:10}")
    private int maxIdle;

    @Value("${spring.data.redis.jedis.pool.min-idle:2}")
    private int minIdle;

    @Value("${spring.data.redis.jedis.pool.max-wait:5000ms}")
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration maxWait;

    @Bean
    public JedisPoolConfig jedisPoolConfig() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxWait(maxWait);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        return poolConfig;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(redisDatabase);
        logger.debug("Redis host: {}, port: {}, database: {}", redisHost, redisPort, redisDatabase);

        if (StringUtils.hasText(redisPassword)) {
            config.setPassword(redisPassword);
            logger.debug("Redis password set");
        }

        JedisClientConfiguration.JedisClientConfigurationBuilder clientConfigBuilder =
            JedisClientConfiguration.builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .usePooling()
                .poolConfig(jedisPoolConfig()).and();

        if (useSsl) {
            clientConfigBuilder.useSsl();
        }
        
        JedisClientConfiguration clientConfig = clientConfigBuilder.build();
        
        return new JedisConnectionFactory(config, clientConfig);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }
}
