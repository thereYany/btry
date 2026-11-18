package com.wherelee.base.modules.platform.storage;

import tools.jackson.databind.ObjectMapper;
import com.wherelee.base.modules.platform.resilience.RedisFallbackMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignedUrlCacheTest {

    @Test
    void fallsBackToLocalGenerationWhenRedisIsDown() {
        CosObjectStorage cos = mock(CosObjectStorage.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        when(cos.presignedGetUrl(anyString(), any(Duration.class)))
                .thenReturn("https://example.test/asset.bin");

        SignedUrlCache cache = new SignedUrlCache(
                cos,
                new StorageProperties(true, "bucket", "region", 240, 3000, 15000, 2, 30),
                redis,
                tools.jackson.databind.json.JsonMapper.builder().build(),
                new SimpleMeterRegistry(),
                CircuitBreakerRegistry.ofDefaults(),
                new RedisFallbackMetrics(new SimpleMeterRegistry()));

        SignedUrlCache.CachedUrl result = cache.get("object-key");

        assertThat(result.url()).isEqualTo("https://example.test/asset.bin");
        assertThat(result.expiresAt()).isNotNull();
    }
}
