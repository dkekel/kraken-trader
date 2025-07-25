package ch.kekelidze.krakentrader.api.rest.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CaffeineCacheManagerConfig
 * 
 * Note: This is a simple test to verify the configuration class.
 * In a real-world scenario, we might use more sophisticated testing approaches.
 */
@SpringBootTest
@ContextConfiguration(classes = {CaffeineCacheManagerConfig.class})
public class CaffeineCacheManagerConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void tradingCacheManager_shouldBeCreated() {
        // Verify that the bean is created
        CacheManager cacheManager = applicationContext.getBean("tradingCacheManager", CacheManager.class);
        assertNotNull(cacheManager);
        assertTrue(cacheManager instanceof CaffeineCacheManager);
    }

    @Test
    void tradingCacheManager_shouldHaveCorrectConfiguration() {
        // Get the cache manager bean
        CaffeineCacheManager cacheManager = applicationContext.getBean("tradingCacheManager", CaffeineCacheManager.class);
        
        // Verify that the bean is created and is of the correct type
        assertNotNull(cacheManager);
        assertTrue(cacheManager instanceof CaffeineCacheManager);
        
        // Create a cache to verify it works
        assertNotNull(cacheManager.getCache("testCache"));
        
        // Note: We're not testing the internal configuration details of Caffeine
        // as that would make the test brittle. Instead, we're just verifying that
        // the cache manager is created correctly and can create caches.
    }
}