package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataCompanyMemberRepository
        extends JpaRepository<CompanyMemberEntity, CompanyMemberId> {

    List<CompanyMemberEntity> findByIdUsername(String username);

    List<CompanyMemberEntity> findByIdUsernameIn(Collection<String> usernames);

    List<CompanyMemberEntity> findByIdCompanyId(UUID companyId);

    Optional<CompanyMemberEntity> findFirstByIdUsernameInAndIdCompanyId(
            Collection<String> usernames,
            UUID companyId
    );

    void deleteByIdUsernameIn(Collection<String> usernames);
}