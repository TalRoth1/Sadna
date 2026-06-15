package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SpringDataRatingRepository extends JpaRepository<RatingEntity, UUID> {

    List<RatingEntity> findByCompanyId(UUID companyId);

    List<RatingEntity> findByCompanyIdIn(Collection<UUID> companyIds);

    void deleteByCompanyId(UUID companyId);
}