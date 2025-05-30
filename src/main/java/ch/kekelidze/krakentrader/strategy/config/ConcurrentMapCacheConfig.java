package ch.kekelidze.krakentrader.strategy.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ConcurrentMapCacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(); // Simple in-memory cache implementation
    }
}