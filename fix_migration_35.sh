#!/bin/bash
# Script to fix failed migration V35
# This script will:
# 1. Drop any partially created wallet tables
# 2. Remove the failed migration record from flyway_schema_history
# 3. Restart the backend

echo "🔧 Fixing failed migration V35..."

# Check if docker-compose is available
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
elif command -v docker &> /dev/null && docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    echo "❌ Error: docker-compose or docker compose not found"
    exit 1
fi

echo "📋 Step 1: Dropping any partially created wallet tables..."
$DOCKER_COMPOSE exec -T db mysql -u familyapp -pfamilyapp familyapp <<EOF
DROP TABLE IF EXISTS wallet_notification;
DROP TABLE IF EXISTS wallet_transaction_savings_goal;
DROP TABLE IF EXISTS savings_goal;
DROP TABLE IF EXISTS wallet_transaction;
DROP TABLE IF EXISTS child_wallet;
DROP TABLE IF EXISTS expense_category;
EOF

echo "✅ Dropped any partially created tables"

echo "📋 Step 2: Checking current state of flyway_schema_history..."
$DOCKER_COMPOSE exec -T db mysql -u familyapp -pfamilyapp familyapp -e "SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version = '35' OR version = '35.1';"

echo "📋 Step 3: Removing failed migration record from flyway_schema_history..."
$DOCKER_COMPOSE exec -T db mysql -u familyapp -pfamilyapp familyapp <<EOF
-- Remove any failed V35 migration records
DELETE FROM flyway_schema_history 
WHERE version = '35' 
AND success = 0;

-- Also remove V35_1 if it exists (repair migration)
DELETE FROM flyway_schema_history 
WHERE version = '35.1';
EOF

echo "✅ Removed failed migration records"

echo "📋 Step 4: Verifying cleanup..."
$DOCKER_COMPOSE exec -T db mysql -u familyapp -pfamilyapp familyapp -e "SELECT version, description, success FROM flyway_schema_history WHERE version = '35' OR version = '35.1';"

echo "📋 Step 5: Restarting backend..."
$DOCKER_COMPOSE restart backend

echo "✅ Backend restarted"
echo ""
echo "🎉 Done! The backend should now start successfully and migration V35 will run again."
echo "   Check the logs with: $DOCKER_COMPOSE logs -f backend"
