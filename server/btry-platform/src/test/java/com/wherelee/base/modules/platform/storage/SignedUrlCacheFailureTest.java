package com.wherelee.base.modules.platform.storage;

import tools.jackson.databind.ObjectMapper;
import com.wherelee.base.modules.platform.resilience.RedisFallbackMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignedUrlCacheFailureTest {

    @Test
    void throwsClearFailureWhenCosPresignFails() {
        CosObjectStorage cos = mock(CosObjectStorage.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        when(cos.presignedGetUrl(anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("COS credentials invalid"));

        SignedUrlCache cache = new SignedUrlCache(
                cos,
                new StorageProperties(true, "bucket", "region", 240, 3000, 15000, 2, 30),
                redis,
                tools.jackson.databind.json.JsonMapper.builder().build(),
                new SimpleMeterRegistry(),
                CircuitBreakerRegistry.ofDefaults(),
                new RedisFallbackMetrics(new SimpleMeterRegistry()));

        assertThatThrownBy(() -> cache.get("object-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COS credentials invalid");
    }
}
