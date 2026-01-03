package com.familyapp.infrastructure.todo;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TodoListJpaRepository extends JpaRepository<TodoListEntity, UUID> {

    @EntityGraph(attributePaths = "items")
    List<TodoListEntity> findAllWithItemsBy();
}



