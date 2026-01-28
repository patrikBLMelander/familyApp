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
     * Get a member by device token from cache.
     * 
     * @param deviceToken The device token
     * @return The cached member, or null if not in cache
     */
    public FamilyMember getDeviceToken(String deviceToken) {
        return get("deviceTokens", deviceToken, FamilyMember.class);
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
     * SECURITY: familyId must never be null - caching with "all" key would expose all families' members
     * 
     * @param familyId The family ID (must not be null)
     * @param members The members list to cache
     */
    public void putFamilyMembers(UUID familyId, List<FamilyMember> members) {
        if (familyId == null) {
            log.warn("SECURITY: Attempted to cache family members with null familyId - ignoring");
            return;
        }
        put("familyMembers", familyId.toString(), members);
    }

    /**
     * Evict family members cache for a specific family.
     * 
     * SECURITY: familyId must never be null - evicting "all" cache is a security risk
     * 
     * @param familyId The family ID to evict (must not be null)
     */
    public void evictFamilyMembers(UUID familyId) {
        if (familyId == null) {
            log.warn("SECURITY: Attempted to evict family members cache with null familyId - ignoring");
            return;
        }
        evict("familyMembers", familyId.toString());
    }

    // ========== Categories Cache Operations ==========

    /**
     * Evict categories cache for a specific family.
     * 
     * SECURITY: familyId must never be null - evicting "all" cache is a security risk
     * 
     * @param familyId The family ID to evict (must not be null)
     */
    public void evictCategories(UUID familyId) {
        if (familyId == null) {
            log.warn("SECURITY: Attempted to evict categories cache with null familyId - ignoring");
            return;
        }
        evict("categories", familyId.toString());
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
     * Get a value from a cache.
     * 
     * @param cacheName The cache name
     * @param key The cache key
     * @param type The expected type
     * @return The cached value, or null if not found
     */
    @SuppressWarnings("unchecked")
    private <T> T get(String cacheName, String key, Class<T> type) {
        if (key == null) {
            return null;
        }

        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Cache.ValueWrapper wrapper = cache.get(key);
                if (wrapper != null) {
                    Object value = wrapper.get();
                    if (type.isInstance(value)) {
                        return (T) value;
                    }
                }
            }
        } catch (Exception e) {
            // Log but don't fail the operation
            log.warn("Failed to get {}:{} from cache {}: {}", 
                key, cacheName, e.getMessage());
        }
        return null;
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
