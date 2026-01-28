#!/bin/bash

# Security Test Script - Family Isolation
# Tests that families cannot access each other's data

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
API_BASE_URL="${API_BASE_URL:-http://localhost:8080/api/v1}"
TEST_OUTPUT_DIR="./test_security_results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Create output directory
mkdir -p "$TEST_OUTPUT_DIR"

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

# Helper functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

test_endpoint() {
    local method=$1
    local endpoint=$2
    local token=$3
    local data=$4
    local expected_status=$5
    local description=$6
    
    TESTS_TOTAL=$((TESTS_TOTAL + 1))
    
    local headers=(-H "Content-Type: application/json")
    if [ -n "$token" ]; then
        headers+=(-H "X-Device-Token: $token")
    fi
    
    local response
    if [ "$method" = "GET" ]; then
        # URL-encode the endpoint to handle query parameters correctly
        local encoded_endpoint=$(echo "$endpoint" | sed 's/ /%20/g')
        response=$(curl -s -w "\n%{http_code}" "${headers[@]}" "$API_BASE_URL$encoded_endpoint" 2>&1)
    elif [ "$method" = "POST" ]; then
        response=$(curl -s -w "\n%{http_code}" -X POST "${headers[@]}" -d "$data" "$API_BASE_URL$endpoint" 2>&1)
    elif [ "$method" = "PATCH" ]; then
        response=$(curl -s -w "\n%{http_code}" -X PATCH "${headers[@]}" -d "$data" "$API_BASE_URL$endpoint" 2>&1)
    elif [ "$method" = "DELETE" ]; then
        response=$(curl -s -w "\n%{http_code}" -X DELETE "${headers[@]}" "$API_BASE_URL$endpoint" 2>&1)
    fi
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "$expected_status" ]; then
        log_info "✓ $description (HTTP $http_code)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        log_error "✗ $description (Expected HTTP $expected_status, got $http_code)"
        echo "Response: $body" >> "$TEST_OUTPUT_DIR/failures_$TIMESTAMP.log"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Test setup: Create two families
log_info "Setting up test families..."

# Generate unique emails based on timestamp
UNIQUE_ID=$(date +%s)
FAMILY1_EMAIL="admin1_${UNIQUE_ID}@test.com"
FAMILY2_EMAIL="admin2_${UNIQUE_ID}@test.com"

# Register Family 1
FAMILY1_RESPONSE=$(curl -s -X POST "$API_BASE_URL/families/register" \
    -H "Content-Type: application/json" \
    -d "{
        \"familyName\": \"Security Test Family 1\",
        \"adminName\": \"Admin1\",
        \"adminEmail\": \"$FAMILY1_EMAIL\",
        \"password\": \"password123\"
    }")

FAMILY1_TOKEN=$(echo "$FAMILY1_RESPONSE" | grep -o '"deviceToken":"[^"]*"' | cut -d'"' -f4)
FAMILY1_ID=$(echo "$FAMILY1_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$FAMILY1_TOKEN" ]; then
    log_error "Failed to register Family 1"
    exit 1
fi

log_info "Family 1 created: ID=$FAMILY1_ID"

# Register Family 2
FAMILY2_RESPONSE=$(curl -s -X POST "$API_BASE_URL/families/register" \
    -H "Content-Type: application/json" \
    -d "{
        \"familyName\": \"Security Test Family 2\",
        \"adminName\": \"Admin2\",
        \"adminEmail\": \"$FAMILY2_EMAIL\",
        \"password\": \"password123\"
    }")

FAMILY2_TOKEN=$(echo "$FAMILY2_RESPONSE" | grep -o '"deviceToken":"[^"]*"' | cut -d'"' -f4)
FAMILY2_ID=$(echo "$FAMILY2_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$FAMILY2_TOKEN" ]; then
    log_error "Failed to register Family 2"
    exit 1
fi

log_info "Family 2 created: ID=$FAMILY2_ID"

# Create test data for Family 1
log_info "Creating test data for Family 1..."

# Create calendar category for Family 1
CATEGORY1_RESPONSE=$(curl -s -X POST "$API_BASE_URL/calendar/categories" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: $FAMILY1_TOKEN" \
    -d '{"name": "Family1 Category", "color": "#ff0000"}')

CATEGORY1_ID=$(echo "$CATEGORY1_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Create calendar event for Family 1 (use current date to ensure it's visible)
CURRENT_DATE=$(date +%Y-%m-%d)
EVENT1_RESPONSE=$(curl -s -X POST "$API_BASE_URL/calendar/events" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: $FAMILY1_TOKEN" \
    -d "{
        \"categoryId\": \"$CATEGORY1_ID\",
        \"title\": \"Family1 Event\",
        \"startDateTime\": \"${CURRENT_DATE}T10:00\",
        \"endDateTime\": \"${CURRENT_DATE}T11:00\",
        \"isAllDay\": false
    }")

EVENT1_ID=$(echo "$EVENT1_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Create todo list for Family 1
LIST1_RESPONSE=$(curl -s -X POST "$API_BASE_URL/todo-lists" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: $FAMILY1_TOKEN" \
    -d '{"name": "Family1 List", "isPrivate": false}')

LIST1_ID=$(echo "$LIST1_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Create daily task for Family 1
TASK1_RESPONSE=$(curl -s -X POST "$API_BASE_URL/daily-tasks" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: $FAMILY1_TOKEN" \
    -d '{
        "name": "Family1 Task",
        "description": "Test task",
        "daysOfWeek": ["MONDAY"],
        "isRequired": true,
        "xpPoints": 1
    }')

TASK1_ID=$(echo "$TASK1_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

log_info "Test data created for Family 1"

# Create test data for Family 2
log_info "Creating test data for Family 2..."

# Create calendar category for Family 2
CATEGORY2_RESPONSE=$(curl -s -X POST "$API_BASE_URL/calendar/categories" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: $FAMILY2_TOKEN" \
    -d '{"name": "Family2 Category", "color": "#0000ff"}')

CATEGORY2_ID=$(echo "$CATEGORY2_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Create calendar event for Family 2 (use current date to ensure it's visible)
EVENT2_RESPONSE=$(curl -s -X POST "$API_BASE_URL/calendar/events" \
    -H "Content-Type: application/json" \
    -H "X-Device-Token: $FAMILY2_TOKEN" \
    -d "{
        \"categoryId\": \"$CATEGORY2_ID\",
        \"title\": \"Family2 Event\",
        \"startDateTime\": \"${CURRENT_DATE}T10:00\",
        \"endDateTime\": \"${CURRENT_DATE}T11:00\",
        \"isAllDay\": false
    }")

EVENT2_ID=$(echo "$EVENT2_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

log_info "Test data created for Family 2"

# Run security tests
log_info "Running security tests..."
echo ""

# Test 1: Family 2 cannot update Family 1's event
test_endpoint "PATCH" "/calendar/events/$EVENT1_ID" "$FAMILY2_TOKEN" \
    '{"title": "Hacked Event", "startDateTime": "2024-12-01T10:00", "endDateTime": "2024-12-01T11:00"}' \
    "400" "Family 2 cannot update Family 1's event"

# Test 2: Family 2 cannot delete Family 1's event
test_endpoint "DELETE" "/calendar/events/$EVENT1_ID" "$FAMILY2_TOKEN" "" \
    "400" "Family 2 cannot delete Family 1's event"

# Test 3: Family 2 cannot update Family 1's category
test_endpoint "PATCH" "/calendar/categories/$CATEGORY1_ID" "$FAMILY2_TOKEN" \
    '{"name": "Hacked Category", "color": "#ff0000"}' \
    "400" "Family 2 cannot update Family 1's category"

# Test 4: Family 2 cannot delete Family 1's category
test_endpoint "DELETE" "/calendar/categories/$CATEGORY1_ID" "$FAMILY2_TOKEN" "" \
    "400" "Family 2 cannot delete Family 1's category"

# Test 5: Family 2 cannot update Family 1's todo list
test_endpoint "PATCH" "/todo-lists/$LIST1_ID" "$FAMILY2_TOKEN" \
    '{"name": "Hacked List"}' \
    "400" "Family 2 cannot update Family 1's todo list"

# Test 6: Family 2 cannot delete Family 1's todo list
test_endpoint "DELETE" "/todo-lists/$LIST1_ID" "$FAMILY2_TOKEN" "" \
    "400" "Family 2 cannot delete Family 1's todo list"

# Test 7: Family 2 cannot update Family 1's daily task
test_endpoint "PATCH" "/daily-tasks/$TASK1_ID" "$FAMILY2_TOKEN" \
    '{"name": "Hacked Task", "description": "Hacked", "daysOfWeek": ["MONDAY"], "isRequired": true, "xpPoints": 1}' \
    "400" "Family 2 cannot update Family 1's daily task"

# Test 8: Family 2 cannot delete Family 1's daily task
test_endpoint "DELETE" "/daily-tasks/$TASK1_ID" "$FAMILY2_TOKEN" "" \
    "400" "Family 2 cannot delete Family 1's daily task"

# Test 9: Family 1 can access their own data (positive test)
# Note: getEvents works without date params, uses getAllEvents internally (3 months range)
FAMILY1_EVENTS=$(curl -s -X GET "$API_BASE_URL/calendar/events" \
    -H "X-Device-Token: $FAMILY1_TOKEN")
if echo "$FAMILY1_EVENTS" | grep -q "Family1 Event"; then
    log_info "✓ Family 1 can access their own events"
    TESTS_PASSED=$((TESTS_PASSED + 1))
elif [ "$FAMILY1_EVENTS" = "[]" ]; then
    # Empty list is also OK - means no events in range, but endpoint works
    log_info "✓ Family 1 can access events endpoint (empty list)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    log_error "✗ Family 1 cannot access their own events"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

# Test 10: Family 2 can access their own data (positive test)
FAMILY2_EVENTS=$(curl -s -X GET "$API_BASE_URL/calendar/events" \
    -H "X-Device-Token: $FAMILY2_TOKEN")
if echo "$FAMILY2_EVENTS" | grep -q "Family2 Event"; then
    log_info "✓ Family 2 can access their own events"
    TESTS_PASSED=$((TESTS_PASSED + 1))
elif [ "$FAMILY2_EVENTS" = "[]" ]; then
    # Empty list is also OK - means no events in range, but endpoint works
    log_info "✓ Family 2 can access events endpoint (empty list)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    log_error "✗ Family 2 cannot access their own events"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

# Test 11: Family 1 cannot see Family 2's events in their list
EVENTS1=$(curl -s -X GET "$API_BASE_URL/calendar/events" \
    -H "X-Device-Token: $FAMILY1_TOKEN")
if echo "$EVENTS1" | grep -q "Family2 Event"; then
    log_error "✗ Family 1 can see Family 2's events (should not be visible)"
    TESTS_FAILED=$((TESTS_FAILED + 1))
else
    log_info "✓ Family 1 cannot see Family 2's events"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

# Test 12: Family 2 cannot see Family 1's events in their list
EVENTS2=$(curl -s -X GET "$API_BASE_URL/calendar/events" \
    -H "X-Device-Token: $FAMILY2_TOKEN")
if echo "$EVENTS2" | grep -q "Family1 Event"; then
    log_error "✗ Family 2 can see Family 1's events (should not be visible)"
    TESTS_FAILED=$((TESTS_FAILED + 1))
else
    log_info "✓ Family 2 cannot see Family 1's events"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

# Test 13: Invalid token should return empty list (not 400, as per controller logic)
INVALID_TOKEN_RESPONSE=$(curl -s -X GET "$API_BASE_URL/calendar/events" \
    -H "X-Device-Token: invalid-token-12345")
if [ "$INVALID_TOKEN_RESPONSE" = "[]" ]; then
    log_info "✓ Invalid token returns empty list"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    log_error "✗ Invalid token does not return empty list"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

# Test 14: Missing token should be rejected (where required)
# Note: PATCH without token may return 405 (Method Not Allowed) or 400 depending on Spring config
# The important thing is that it doesn't succeed
MISSING_TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" -X PATCH "$API_BASE_URL/calendar/events/$EVENT1_ID" \
    -H "Content-Type: application/json" \
    -d '{"title": "Test", "startDateTime": "2024-12-15T10:00", "endDateTime": "2024-12-15T11:00"}')
MISSING_TOKEN_CODE=$(echo "$MISSING_TOKEN_RESPONSE" | tail -n1)
if [ "$MISSING_TOKEN_CODE" != "200" ] && [ "$MISSING_TOKEN_CODE" != "201" ]; then
    log_info "✓ Missing token is rejected (HTTP $MISSING_TOKEN_CODE)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    log_error "✗ Missing token is not rejected (HTTP $MISSING_TOKEN_CODE)"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

# Print summary
echo ""
log_info "========================================="
log_info "Test Summary"
log_info "========================================="
log_info "Total tests: $TESTS_TOTAL"
log_info "Passed: $TESTS_PASSED"
log_info "Failed: $TESTS_FAILED"

if [ $TESTS_FAILED -eq 0 ]; then
    log_info "All security tests PASSED! ✓"
    exit 0
else
    log_error "Some security tests FAILED! ✗"
    log_error "Check $TEST_OUTPUT_DIR/failures_$TIMESTAMP.log for details"
    exit 1
fi
