package com.familyapp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for performance optimization.
 * Uses Caffeine cache (high-performance, bounded cache with TTL).
 * 
 * Cache Configuration:
 * - deviceTokens: 30 min TTL, max 10k entries (most frequently used)
 * - familyMembers: 30 min TTL, max 5k entries
 * - categories: 1 hour TTL, max 1k entries (rarely changes)
 * - members: 30 min TTL, max 10k entries
 * - petTypes: 24 hour TTL, max 100 entries (static data)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * Configure cache manager with Caffeine (high-performance cache).
     * 
     * Features:
     * - TTL (Time To Live): Automatic expiration
     * - Size limits: Prevents memory exhaustion
     * - Statistics: Built-in metrics
     * - Thread-safe: Concurrent access
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configure deviceTokens cache (most frequently used)
        cacheManager.registerCustomCache("deviceTokens", Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)  // 30 min TTL
            .maximumSize(10_000)                      // Max 10k entries
            .recordStats()                            // Enable metrics
            .build());
        
        // Configure familyMembers cache
        cacheManager.registerCustomCache("familyMembers", Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)  // 30 min TTL
            .maximumSize(5_000)                       // Max 5k entries
            .recordStats()
            .build());
        
        // Configure categories cache (rarely changes, longer TTL)
        cacheManager.registerCustomCache("categories", Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)      // 1 hour TTL
            .maximumSize(1_000)                       // Max 1k entries
            .recordStats()
            .build());
        
        // Configure members cache
        cacheManager.registerCustomCache("members", Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)  // 30 min TTL
            .maximumSize(10_000)                      // Max 10k entries
            .recordStats()
            .build());
        
        // Configure petTypes cache (static data, very long TTL)
        cacheManager.registerCustomCache("petTypes", Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)     // 24 hour TTL
            .maximumSize(100)                         // Max 100 entries
            .recordStats()
            .build());
        
        log.info("=== Cache Manager Initialized (Caffeine) ===");
        log.info("Caches configured:");
        log.info("  - deviceTokens: 30min TTL, max 10k entries");
        log.info("  - familyMembers: 30min TTL, max 5k entries");
        log.info("  - categories: 1hour TTL, max 1k entries");
        log.info("  - members: 30min TTL, max 10k entries");
        log.info("  - petTypes: 24hour TTL, max 100 entries");
        log.info("============================================");
        
        return cacheManager;
    }
}
