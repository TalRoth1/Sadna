package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataCompanyMemberRepository extends JpaRepository<CompanyMemberEntity, UUID> {

    List<CompanyMemberEntity> findByUsername(String username);

    List<CompanyMemberEntity> findByUsernameIn(Collection<String> usernames);

    List<CompanyMemberEntity> findByCompanyId(UUID companyId);

    Optional<CompanyMemberEntity> findFirstByUsernameInAndCompanyId(Collection<String> usernames, UUID companyId);

    void deleteByUsernameIn(Collection<String> usernames);
}