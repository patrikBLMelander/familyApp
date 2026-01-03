package com.familyapp.infrastructure.todo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TodoItemJpaRepository extends JpaRepository<TodoItemEntity, UUID> {
}



