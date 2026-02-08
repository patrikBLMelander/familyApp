-- Create wallet tables for children's digital currency system
-- SEK without öre (INTEGER, not DECIMAL)

-- Expense categories (default: Godis, Leksaker, Kläder)
CREATE TABLE expense_category (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    emoji VARCHAR(10),
    is_default BOOLEAN DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

-- Insert default categories
INSERT INTO expense_category (id, name, emoji, is_default, created_at) VALUES
(UUID(), 'Godis', '🍬', TRUE, NOW()),
(UUID(), 'Leksaker', '🧸', TRUE, NOW()),
(UUID(), 'Kläder', '👕', TRUE, NOW());

-- Child wallet (one per member)
CREATE TABLE child_wallet (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL UNIQUE,
    balance INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE
);

-- Wallet transactions (must be created before savings_goal due to foreign key reference)
CREATE TABLE wallet_transaction (
    id VARCHAR(36) PRIMARY KEY,
    wallet_id VARCHAR(36) NOT NULL,
    amount INTEGER NOT NULL,
    transaction_type VARCHAR(20) NOT NULL, -- 'ALLOWANCE', 'EXPENSE', 'MANUAL_ADJUSTMENT', 'DELETION'
    description VARCHAR(255),
    category_id VARCHAR(36),
    created_by_member_id VARCHAR(36),
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at DATETIME(6),
    deleted_by_member_id VARCHAR(36),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (wallet_id) REFERENCES child_wallet(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES expense_category(id),
    FOREIGN KEY (created_by_member_id) REFERENCES family_member(id),
    FOREIGN KEY (deleted_by_member_id) REFERENCES family_member(id)
);

-- Savings goals (references wallet_transaction, so must be created after)
CREATE TABLE savings_goal (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    target_amount INTEGER NOT NULL,
    current_amount INTEGER NOT NULL DEFAULT 0,
    emoji VARCHAR(10),
    is_active BOOLEAN DEFAULT TRUE,
    is_completed BOOLEAN DEFAULT FALSE,
    is_purchased BOOLEAN DEFAULT FALSE,
    completed_at DATETIME(6),
    purchased_at DATETIME(6),
    purchase_transaction_id VARCHAR(36),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    FOREIGN KEY (purchase_transaction_id) REFERENCES wallet_transaction(id)
);

-- Junction table for multiple savings goals per transaction
CREATE TABLE wallet_transaction_savings_goal (
    transaction_id VARCHAR(36) NOT NULL,
    savings_goal_id VARCHAR(36) NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (transaction_id, savings_goal_id),
    FOREIGN KEY (transaction_id) REFERENCES wallet_transaction(id) ON DELETE CASCADE,
    FOREIGN KEY (savings_goal_id) REFERENCES savings_goal(id) ON DELETE CASCADE
);

-- Wallet notifications (for popup when receiving money)
CREATE TABLE wallet_notification (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL,
    transaction_id VARCHAR(36) NOT NULL,
    amount INTEGER NOT NULL,
    description VARCHAR(255),
    shown_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    FOREIGN KEY (transaction_id) REFERENCES wallet_transaction(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_child_wallet_member_id ON child_wallet(member_id);
CREATE INDEX idx_savings_goal_member_id ON savings_goal(member_id);
CREATE INDEX idx_savings_goal_active ON savings_goal(member_id, is_active, is_completed);
CREATE INDEX idx_wallet_transaction_wallet_id ON wallet_transaction(wallet_id);
CREATE INDEX idx_wallet_transaction_created_at ON wallet_transaction(wallet_id, created_at DESC);
CREATE INDEX idx_wallet_transaction_category ON wallet_transaction(category_id);
CREATE INDEX idx_wallet_transaction_savings_goal_transaction ON wallet_transaction_savings_goal(transaction_id);
CREATE INDEX idx_wallet_transaction_savings_goal_goal ON wallet_transaction_savings_goal(savings_goal_id);
CREATE INDEX idx_wallet_notification_member_unshown ON wallet_notification(member_id, shown_at);
