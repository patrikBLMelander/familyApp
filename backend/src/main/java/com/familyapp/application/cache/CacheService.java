package com.familyapp.application.cache;

import com.familyapp.domain.familymember.FamilyMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Centralized cache service to eliminate code duplication.
 * 
 * Provides type-safe methods for common cache operations with proper error handling.
 * All cache operations are wrapped in try-catch to prevent cache failures from
 * breaking the application.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final CacheManager cacheManager;

    public CacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // ========== Member Cache Operations ==========

    /**
     * Cache a member by ID.
     * 
     * @param memberId The member ID
     * @param member The member to cache
     */
    public void putMember(UUID memberId, FamilyMember member) {
        put("members", memberId != null ? memberId.toString() : null, member);
    }

    /**
     * Evict a member from cache by ID.
     * 
     * @param memberId The member ID to evict
     */
    public void evictMember(UUID memberId) {
        evict("members", memberId != null ? memberId.toString() : null);
    }

    // ========== Device Token Cache Operations ==========

    /**
     * Cache a member by device token.
     * 
     * @param deviceToken The device token
     * @param member The member to cache
     */
    public void putDeviceToken(String deviceToken, FamilyMember member) {
        put("deviceTokens", deviceToken, member);
    }

    /**
     * Evict a device token from cache.
     * 
     * @param deviceToken The device token to evict
     */
    public void evictDeviceToken(String deviceToken) {
        evict("deviceTokens", deviceToken);
    }

    // ========== Family Members Cache Operations ==========

    /**
     * Cache family members list by family ID.
     * 
     * @param familyId The family ID
     * @param members The members list to cache
     */
    public void putFamilyMembers(UUID familyId, List<FamilyMember> members) {
        put("familyMembers", familyId != null ? familyId.toString() : "all", members);
    }

    /**
     * Evict family members cache for a specific family.
     * 
     * @param familyId The family ID to evict
     */
    public void evictFamilyMembers(UUID familyId) {
        evict("familyMembers", familyId != null ? familyId.toString() : "all");
    }

    // ========== Categories Cache Operations ==========

    /**
     * Evict categories cache for a specific family.
     * 
     * @param familyId The family ID to evict
     */
    public void evictCategories(UUID familyId) {
        evict("categories", familyId != null ? familyId.toString() : "all");
    }

    // ========== Generic Cache Operations ==========

    /**
     * Put a value into a cache.
     * 
     * @param cacheName The cache name
     * @param key The cache key
     * @param value The value to cache
     */
    private void put(String cacheName, String key, Object value) {
        if (key == null || value == null) {
            return;
        }

        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, value);
            }
        } catch (Exception e) {
            // Log but don't fail the operation
            // Cache failures should not break the application
            log.warn("Failed to cache {}:{} in cache {}: {}", 
                key, cacheName, e.getMessage());
        }
    }

    /**
     * Evict a value from a cache.
     * 
     * @param cacheName The cache name
     * @param key The cache key to evict
     */
    private void evict(String cacheName, String key) {
        if (key == null) {
            return;
        }

        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (Exception e) {
            // Log but don't fail the operation
            log.warn("Failed to evict {}:{} from cache {}: {}", 
                key, cacheName, e.getMessage());
        }
    }
}
