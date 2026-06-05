package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SpringDataInvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    List<InvitationEntity> findByAppointeeUsername(String appointeeUsername);

    List<InvitationEntity> findByAppointeeUsernameIn(Collection<String> appointeeUsernames);

    void deleteByAppointeeUsernameIn(Collection<String> appointeeUsernames);
}