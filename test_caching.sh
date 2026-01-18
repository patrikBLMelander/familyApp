#!/bin/bash

# Simple script to test caching
# Usage: ./test_caching.sh <device-token>

DEVICE_TOKEN=$1

if [ -z "$DEVICE_TOKEN" ]; then
    echo "Usage: ./test_caching.sh <device-token>"
    echo ""
    echo "To get a device token:"
    echo "1. Register a family:"
    echo "   curl -X POST http://localhost:8080/api/v1/families/register \\"
    echo "     -H 'Content-Type: application/json' \\"
    echo "     -d '{\"familyName\":\"Test\",\"adminName\":\"Test\",\"adminEmail\":\"test@test.com\",\"password\":\"test123\"}'"
    echo ""
    echo "2. Or use an existing device token from your app"
    exit 1
fi

echo "=== Testing Caching ==="
echo "Device Token: $DEVICE_TOKEN"
echo ""

echo "Test 1: Device Token Caching (Most Important)"
echo "Making 5 requests to /api/v1/family-members..."
echo ""

for i in {1..5}; do
    echo -n "Request $i: "
    START=$(date +%s%N)
    curl -s -X GET "http://localhost:8080/api/v1/family-members" \
        -H "X-Device-Token: $DEVICE_TOKEN" > /dev/null
    END=$(date +%s%N)
    DURATION=$((($END - $START) / 1000000))
    echo "${DURATION}ms"
done

echo ""
echo "Expected: First request slower (DB query), subsequent requests faster (cache)"
echo ""

echo "Test 2: Categories Caching"
echo "Making 5 requests to /api/v1/calendar/categories..."
echo ""

for i in {1..5}; do
    echo -n "Request $i: "
    START=$(date +%s%N)
    curl -s -X GET "http://localhost:8080/api/v1/calendar/categories" \
        -H "X-Device-Token: $DEVICE_TOKEN" > /dev/null
    END=$(date +%s%N)
    DURATION=$((($END - $START) / 1000000))
    echo "${DURATION}ms"
done

echo ""
echo "=== Test Complete ==="
echo ""
echo "To see cache hits/misses, enable cache logging in application.yml:"
echo "  logging:"
echo "    level:"
echo "      org.springframework.cache: DEBUG"
