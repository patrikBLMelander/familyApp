-- Todo list domain (MySQL syntax)

CREATE TABLE todo_list (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    position    INTEGER      NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE todo_item (
    id           VARCHAR(36) PRIMARY KEY,
    list_id      VARCHAR(36)     NOT NULL,
    description  VARCHAR(500) NOT NULL,
    done         BOOLEAN      NOT NULL DEFAULT FALSE,
    position     INTEGER      NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6),
    FOREIGN KEY (list_id) REFERENCES todo_list (id) ON DELETE CASCADE
);

CREATE INDEX idx_todo_item_list_id ON todo_item (list_id);
CREATE INDEX idx_todo_list_position ON todo_list (position);



