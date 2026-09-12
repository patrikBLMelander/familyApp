package com.familyapp.infrastructure.adventure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LootItemJpaRepository extends JpaRepository<LootItemEntity, String> {

    List<LootItemEntity> findByTypeAndActiveTrue(String type);

    List<LootItemEntity> findByActiveTrue();
}
