#!/bin/bash

# Security Fixes Test Script
# This script helps test the security fixes for cross-family access prevention

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
API_BASE_URL="${API_BASE_URL:-http://localhost:8080/api/v1}"

# Test counters
PASSED=0
FAILED=0

# Function to print test header
print_test() {
    echo ""
    echo -e "${YELLOW}=== $1 ===${NC}"
}

# Function to print result
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ PASS${NC}"
        ((PASSED++))
    else
        echo -e "${RED}❌ FAIL${NC}"
        ((FAILED++))
    fi
}

# Function to make API request and check response
test_request() {
    local method=$1
    local endpoint=$2
    local token=$3
    local body=$4
    local expected_status=$5
    local expected_message=$6
    
    local url="${API_BASE_URL}${endpoint}"
    local headers=(-H "Content-Type: application/json")
    
    if [ -n "$token" ]; then
        headers+=(-H "X-Device-Token: $token")
    fi
    
    if [ -n "$body" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
            "${headers[@]}" \
            -d "$body" 2>/dev/null)
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
            "${headers[@]}" 2>/dev/null)
    fi
    
    http_code=$(echo "$response" | tail -n1)
    response_body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -eq "$expected_status" ]; then
        if [ -n "$expected_message" ]; then
            if echo "$response_body" | grep -q "$expected_message"; then
                return 0
            else
                echo "  Expected message: $expected_message"
                echo "  Actual response: $response_body"
                return 1
            fi
        else
            return 0
        fi
    else
        echo "  Expected status: $expected_status"
        echo "  Actual status: $http_code"
        echo "  Response: $response_body"
        return 1
    fi
}

# Check if required variables are set
if [ -z "$FAMILY_A_TOKEN" ] || [ -z "$FAMILY_B_TOKEN" ]; then
    echo -e "${RED}Error: Required environment variables not set!${NC}"
    echo ""
    echo "Please set the following variables:"
    echo "  export FAMILY_A_TOKEN='your-family-a-device-token'"
    echo "  export FAMILY_B_TOKEN='your-family-b-device-token'"
    echo ""
    echo "Optional:"
    echo "  export FAMILY_A_TASK_ID='task-id-from-family-a'"
    echo "  export FAMILY_B_TASK_ID='task-id-from-family-b'"
    echo "  export FAMILY_A_LIST_ID='list-id-from-family-a'"
    echo "  export FAMILY_B_LIST_ID='list-id-from-family-b'"
    echo "  export API_BASE_URL='http://localhost:8080/api/v1' (default)"
    exit 1
fi

echo -e "${YELLOW}Security Fixes Test Suite${NC}"
echo "API Base URL: $API_BASE_URL"
echo ""

# Test 1: Update Task - Cross-Family Denied
if [ -n "$FAMILY_A_TASK_ID" ]; then
    print_test "Test 1: Update Task - Cross-Family Denied"
    test_request "PATCH" "/daily-tasks/$FAMILY_A_TASK_ID" "$FAMILY_B_TOKEN" \
        '{"name":"Hacked Task","description":"Test","daysOfWeek":["MONDAY"],"isRequired":true,"xpPoints":10}' \
        400 "Access denied"
    print_result $?
else
    echo -e "${YELLOW}⚠️  Skipping Test 1: FAMILY_A_TASK_ID not set${NC}"
fi

# Test 2: Delete Task - Cross-Family Denied
if [ -n "$FAMILY_A_TASK_ID" ]; then
    print_test "Test 2: Delete Task - Cross-Family Denied"
    test_request "DELETE" "/daily-tasks/$FAMILY_A_TASK_ID" "$FAMILY_B_TOKEN" \
        "" 400 "Access denied"
    print_result $?
else
    echo -e "${YELLOW}⚠️  Skipping Test 2: FAMILY_A_TASK_ID not set${NC}"
fi

# Test 3: Toggle Task - Cross-Family Denied
if [ -n "$FAMILY_A_TASK_ID" ]; then
    print_test "Test 3: Toggle Task - Cross-Family Denied"
    test_request "POST" "/daily-tasks/$FAMILY_A_TASK_ID/toggle" "$FAMILY_B_TOKEN" \
        "" 400 "Access denied"
    print_result $?
else
    echo -e "${YELLOW}⚠️  Skipping Test 3: FAMILY_A_TASK_ID not set${NC}"
fi

# Test 4: Update List - Cross-Family Denied
if [ -n "$FAMILY_A_LIST_ID" ]; then
    print_test "Test 4: Update List Name - Cross-Family Denied"
    test_request "PATCH" "/todo-lists/$FAMILY_A_LIST_ID" "$FAMILY_B_TOKEN" \
        '{"name":"Hacked List"}' 400 "Access denied"
    print_result $?
else
    echo -e "${YELLOW}⚠️  Skipping Test 4: FAMILY_A_LIST_ID not set${NC}"
fi

# Test 5: Delete List - Cross-Family Denied
if [ -n "$FAMILY_A_LIST_ID" ]; then
    print_test "Test 5: Delete List - Cross-Family Denied"
    test_request "DELETE" "/todo-lists/$FAMILY_A_LIST_ID" "$FAMILY_B_TOKEN" \
        "" 400 "Access denied"
    print_result $?
else
    echo -e "${YELLOW}⚠️  Skipping Test 5: FAMILY_A_LIST_ID not set${NC}"
fi

# Test 6: Add Item - Cross-Family Denied
if [ -n "$FAMILY_A_LIST_ID" ]; then
    print_test "Test 6: Add Item - Cross-Family Denied"
    test_request "POST" "/todo-lists/$FAMILY_A_LIST_ID/items" "$FAMILY_B_TOKEN" \
        '{"description":"Hacked item"}' 400 "Access denied"
    print_result $?
else
    echo -e "${YELLOW}⚠️  Skipping Test 6: FAMILY_A_LIST_ID not set${NC}"
fi

# Test 7: Missing Device Token
if [ -n "$FAMILY_A_TASK_ID" ]; then
    print_test "Test 7: Missing Device Token"
    test_request "PATCH" "/daily-tasks/$FAMILY_A_TASK_ID" "" \
        '{"name":"Test","description":"Test","daysOfWeek":["MONDAY"],"isRequired":true,"xpPoints":10}' \
        400 "Device token is required"
    print_result $?
else
    echo -e "${YELLOW}⚠️  Skipping Test 7: FAMILY_A_TASK_ID not set${NC}"
fi

# Summary
echo ""
echo -e "${YELLOW}=== Test Summary ===${NC}"
echo -e "Total tests: $((PASSED + FAILED))"
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Please review the output above.${NC}"
    exit 1
fi
