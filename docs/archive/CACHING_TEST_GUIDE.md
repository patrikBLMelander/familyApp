# Caching Test Guide

## Backend Caching Implementation

### What's Cached

1. **Device Token → Member Lookup** (`getMemberByDeviceToken`)
   - Cache name: `deviceTokens`
   - Key: device token string
   - Impact: Called in EVERY request for authentication

2. **Family Members** (`getAllMembers`)
   - Cache name: `familyMembers`
   - Key: family ID (or 'all' if null)
   - Impact: Called frequently when loading family data

3. **Individual Members** (`getMemberById`)
   - Cache name: `members`
   - Key: member ID
   - Impact: Called when fetching specific member data

4. **Calendar Categories** (`getAllCategories`)
   - Cache name: `categories`
   - Key: family ID (or 'all' if null)
   - Impact: Called when loading calendar views

### How to Test

#### 1. Test Device Token Caching (Most Important)

**Before caching:**
- Every API request queries database for device token
- With 100 requests/second = 100 DB queries/second

**After caching:**
- First request: 1 DB query
- Subsequent requests: 0 DB queries (from cache)
- With 100 requests/second = 1 DB query (then cached)

**Test steps:**
1. Start backend
2. Make multiple API calls with same device token
3. Check database logs - should only see 1 query for device token lookup
4. Subsequent calls should be instant (from cache)

#### 2. Test Family Members Caching

**Test steps:**
1. Call `GET /api/v1/family-members` multiple times
2. First call: DB query
3. Subsequent calls: From cache (no DB query)
4. Update a member → cache should be invalidated
5. Next call: Fresh DB query (cache miss)

#### 3. Test Categories Caching

**Test steps:**
1. Call `GET /api/v1/calendar/categories` multiple times
2. First call: DB query
3. Subsequent calls: From cache
4. Create/update category → cache invalidated
5. Next call: Fresh DB query

### Monitoring Cache Performance

#### Enable Cache Logging

Add to `application.properties`:
```properties
# Log cache hits/misses
logging.level.org.springframework.cache=DEBUG
```

#### Expected Log Output

**Cache Hit:**
```
Cache hit for key 'device-token-123' in cache 'deviceTokens'
```

**Cache Miss:**
```
Cache miss for key 'device-token-123' in cache 'deviceTokens'
```

**Cache Evict:**
```
Evicting all entries from cache 'deviceTokens'
```

### Performance Expectations

**Before Caching:**
- Device token lookup: ~5-10ms per request (DB query)
- Family members: ~10-20ms per request (DB query)
- Categories: ~5-10ms per request (DB query)

**After Caching:**
- Device token lookup: ~0.1ms (cache hit)
- Family members: ~0.1ms (cache hit)
- Categories: ~0.1ms (cache hit)

**Improvement: 50-100x faster for cached data**

### Cache Invalidation

Cache is automatically invalidated when:
- Member is created/updated/deleted
- Category is created/updated/deleted
- Device token is changed

This ensures data consistency.

### Current Implementation

- **Cache Manager**: `ConcurrentMapCacheManager` (in-memory)
- **Cache Type**: Simple in-memory cache (good for single-instance deployments)
- **Future**: Can upgrade to Redis/Caffeine for distributed caching

### Testing Checklist

- [ ] Device token caching works (most frequent call)
- [ ] Family members caching works
- [ ] Categories caching works
- [ ] Cache invalidation works (update member → cache cleared)
- [ ] Cache invalidation works (update category → cache cleared)
- [ ] Performance improvement visible in logs

### Troubleshooting

**Cache not working?**
1. Check that `@EnableCaching` is in `CacheConfig`
2. Check that `spring-boot-starter-cache` is in `pom.xml`
3. Check logs for cache hits/misses
4. Verify methods are public (Spring AOP requirement)

**Cache not invalidating?**
1. Check that `@CacheEvict` is on update methods
2. Verify `allEntries = true` is set correctly
3. Check that methods are called (not bypassed)
