package org.example.InfrastructureLayer.Persistence;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataCompanyRepository extends JpaRepository<CompanyEntity, UUID> {

    List<CompanyEntity> findByStatus(Company.CompanyStatus status);

    boolean existsByName(String name);
}