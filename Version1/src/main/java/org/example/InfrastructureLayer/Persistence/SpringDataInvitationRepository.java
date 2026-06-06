package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SpringDataInvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    List<InvitationEntity> findByApointeeUsername(String apointeeUsername);

    List<InvitationEntity> findByApointeeUsernameIn(Collection<String> apointeeUsernames);

    void deleteByApointeeUsernameIn(Collection<String> apointeeUsernames);
}