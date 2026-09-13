package com.familyapp.infrastructure.adventure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChildInventoryJpaRepository extends JpaRepository<ChildInventoryEntity, UUID> {

    @Query("SELECT i FROM ChildInventoryEntity i WHERE i.member.id = :memberId ORDER BY i.acquiredAt DESC")
    List<ChildInventoryEntity> findByMemberId(@Param("memberId") UUID memberId);

    @Query("SELECT COUNT(i) > 0 FROM ChildInventoryEntity i WHERE i.member.id = :memberId AND i.itemId = :itemId")
    boolean existsByMemberAndItem(@Param("memberId") UUID memberId, @Param("itemId") String itemId);
}
