#!/bin/bash

# Test script for bonus food functionality
# Usage: ./test_bonus_food.sh <parent-device-token> <child-device-token>

PARENT_TOKEN=$1
CHILD_TOKEN=$2
API_BASE="http://localhost:8080/api/v1"

if [ -z "$PARENT_TOKEN" ] || [ -z "$CHILD_TOKEN" ]; then
    echo "Usage: ./test_bonus_food.sh <parent-device-token> <child-device-token>"
    echo ""
    echo "To get device tokens:"
    echo "1. Log in as parent in the app and check localStorage for 'deviceToken'"
    echo "2. Log in as child in the app and check localStorage for 'deviceToken'"
    exit 1
fi

echo "=== Testing Bonus Food Functionality ==="
echo "Parent Token: ${PARENT_TOKEN:0:20}..."
echo "Child Token: ${CHILD_TOKEN:0:20}..."
echo ""

# Get child member ID
echo "Step 1: Getting child member ID..."
CHILD_MEMBER_RESPONSE=$(curl -s -X GET "${API_BASE}/family-members" \
    -H "X-Device-Token: ${CHILD_TOKEN}")

CHILD_MEMBER=$(echo "$CHILD_MEMBER_RESPONSE" | jq -r '.[] | select(.role == "CHILD") | .id' | head -n1)

if [ -z "$CHILD_MEMBER" ] || [ "$CHILD_MEMBER" = "null" ]; then
    echo "❌ ERROR: Could not get child member ID"
    echo "Response: $CHILD_MEMBER_RESPONSE"
    exit 1
fi

echo "✅ Child Member ID: $CHILD_MEMBER"
echo ""

# Get initial food count
echo "Step 2: Getting initial food count..."
INITIAL_FOOD=$(curl -s -X GET "${API_BASE}/pets/collected-food" \
    -H "X-Device-Token: ${CHILD_TOKEN}" | jq -r '.totalCount')

echo "Initial food count: $INITIAL_FOOD"
echo ""

# Test 1: Give bonus food (10 food items)
echo "=== Test 1: Give Bonus Food ==="
echo "Giving 10 bonus food items..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE}/xp/members/${CHILD_MEMBER}/bonus" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: ${PARENT_TOKEN}" \
    -d '{"xpPoints": 10}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Successfully gave 10 bonus food items"
    echo "Response: $BODY" | jq '.'
else
    echo "❌ ERROR: Failed to give bonus food (HTTP $HTTP_CODE)"
    echo "Response: $BODY"
    exit 1
fi
echo ""

# Wait a moment for database to update
sleep 1

# Test 2: Verify food was created
echo "=== Test 2: Verify Food Was Created ==="
FOOD_RESPONSE=$(curl -s -X GET "${API_BASE}/pets/collected-food" \
    -H "X-Device-Token: ${CHILD_TOKEN}")

NEW_FOOD_COUNT=$(echo "$FOOD_RESPONSE" | jq -r '.totalCount')
FOOD_ITEMS=$(echo "$FOOD_RESPONSE" | jq -r '.foodItems | length')

echo "New food count: $NEW_FOOD_COUNT"
echo "Food items: $FOOD_ITEMS"

EXPECTED_COUNT=$((INITIAL_FOOD + 10))
if [ "$NEW_FOOD_COUNT" -eq "$EXPECTED_COUNT" ]; then
    echo "✅ Food count is correct (expected $EXPECTED_COUNT, got $NEW_FOOD_COUNT)"
else
    echo "❌ ERROR: Food count mismatch (expected $EXPECTED_COUNT, got $NEW_FOOD_COUNT)"
    exit 1
fi

# Check that bonus food has null eventId
BONUS_FOOD=$(echo "$FOOD_RESPONSE" | jq -r '.foodItems[] | select(.eventId == null) | .id' | head -n1)
if [ -n "$BONUS_FOOD" ]; then
    echo "✅ Found bonus food with null eventId: ${BONUS_FOOD:0:20}..."
else
    echo "⚠️  WARNING: No bonus food with null eventId found"
fi
echo ""

# Test 3: Feed pet (feed 5 food items)
echo "=== Test 3: Feed Pet ==="
echo "Feeding 5 food items..."
FEED_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE}/pets/feed" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: ${CHILD_TOKEN}" \
    -d '{"xpAmount": 5}')

FEED_HTTP_CODE=$(echo "$FEED_RESPONSE" | tail -n1)
FEED_BODY=$(echo "$FEED_RESPONSE" | sed '$d')

if [ "$FEED_HTTP_CODE" = "200" ]; then
    echo "✅ Successfully fed 5 food items"
else
    echo "❌ ERROR: Failed to feed pet (HTTP $FEED_HTTP_CODE)"
    echo "Response: $FEED_BODY"
    exit 1
fi
echo ""

# Wait a moment for database to update
sleep 1

# Test 4: Verify food was fed
echo "=== Test 4: Verify Food Was Fed ==="
AFTER_FEED_RESPONSE=$(curl -s -X GET "${API_BASE}/pets/collected-food" \
    -H "X-Device-Token: ${CHILD_TOKEN}")

AFTER_FEED_COUNT=$(echo "$AFTER_FEED_RESPONSE" | jq -r '.totalCount')
EXPECTED_AFTER_FEED=$((NEW_FOOD_COUNT - 5))

echo "Food count after feeding: $AFTER_FEED_COUNT"
if [ "$AFTER_FEED_COUNT" -eq "$EXPECTED_AFTER_FEED" ]; then
    echo "✅ Food count is correct after feeding (expected $EXPECTED_AFTER_FEED, got $AFTER_FEED_COUNT)"
else
    echo "❌ ERROR: Food count mismatch after feeding (expected $EXPECTED_AFTER_FEED, got $AFTER_FEED_COUNT)"
    exit 1
fi
echo ""

# Test 5: Error cases
echo "=== Test 5: Error Cases ==="

# Test 5.1: Invalid XP amount (0)
echo "Test 5.1: Try to give 0 food (should fail)..."
ERROR_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE}/xp/members/${CHILD_MEMBER}/bonus" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: ${PARENT_TOKEN}" \
    -d '{"xpPoints": 0}')

ERROR_HTTP_CODE=$(echo "$ERROR_RESPONSE" | tail -n1)
ERROR_BODY=$(echo "$ERROR_RESPONSE" | sed '$d')
if [ "$ERROR_HTTP_CODE" != "200" ]; then
    echo "✅ Correctly rejected 0 food (HTTP $ERROR_HTTP_CODE)"
else
    echo "❌ ERROR: Should have rejected 0 food"
fi

# Test 5.2: Invalid XP amount (>100)
echo "Test 5.2: Try to give 101 food (should fail)..."
ERROR_RESPONSE2=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE}/xp/members/${CHILD_MEMBER}/bonus" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: ${PARENT_TOKEN}" \
    -d '{"xpPoints": 101}')

ERROR_HTTP_CODE2=$(echo "$ERROR_RESPONSE2" | tail -n1)
ERROR_BODY2=$(echo "$ERROR_RESPONSE2" | sed '$d')
if [ "$ERROR_HTTP_CODE2" != "200" ]; then
    echo "✅ Correctly rejected 101 food (HTTP $ERROR_HTTP_CODE2)"
else
    echo "❌ ERROR: Should have rejected 101 food"
fi

# Test 5.3: Missing device token
echo "Test 5.3: Try without device token (should fail)..."
ERROR_RESPONSE3=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE}/xp/members/${CHILD_MEMBER}/bonus" \
    -H "Content-Type: application/json" \
    -d '{"xpPoints": 10}')

ERROR_HTTP_CODE3=$(echo "$ERROR_RESPONSE3" | tail -n1)
ERROR_BODY3=$(echo "$ERROR_RESPONSE3" | sed '$d')
if [ "$ERROR_HTTP_CODE3" != "200" ]; then
    echo "✅ Correctly rejected request without device token (HTTP $ERROR_HTTP_CODE3)"
else
    echo "❌ ERROR: Should have rejected request without device token"
fi
echo ""

# Test 6: Database verification
echo "=== Test 6: Database Verification ==="
echo "Checking database for bonus food items..."

# Get food items from database
DB_FOOD_COUNT=$(docker exec -i familyapp-db mysql -ufamilyapp -pfamilyapp familyapp -e \
    "SELECT COUNT(*) FROM collected_food WHERE member_id = '${CHILD_MEMBER}' AND event_id IS NULL AND is_fed = 0;" \
    2>/dev/null | tail -n1 | tr -d ' ')

if [ -n "$DB_FOOD_COUNT" ] && [ "$DB_FOOD_COUNT" != "NULL" ]; then
    echo "✅ Found $DB_FOOD_COUNT unfed bonus food items in database"
else
    echo "⚠️  Could not verify database (might need to check manually)"
fi

# Check fed food
DB_FED_COUNT=$(docker exec -i familyapp-db mysql -ufamilyapp -pfamilyapp familyapp -e \
    "SELECT COUNT(*) FROM collected_food WHERE member_id = '${CHILD_MEMBER}' AND event_id IS NULL AND is_fed = 1;" \
    2>/dev/null | tail -n1 | tr -d ' ')

if [ -n "$DB_FED_COUNT" ] && [ "$DB_FED_COUNT" != "NULL" ]; then
    echo "✅ Found $DB_FED_COUNT fed bonus food items in database"
fi
echo ""

echo "=== Test Summary ==="
echo "✅ All critical tests passed!"
echo ""
echo "Next steps for manual testing:"
echo "1. Log in as child and verify food is visible in dashboard"
echo "2. Feed the pet and verify XP increases"
echo "3. Verify level up works correctly"
echo "4. Test UI/UX elements (confetti, animations, etc.)"
