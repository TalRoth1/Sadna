package org.example.DomainLayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;

public interface ICompanyRepository {
    UUID createCompany(String founderEmail, String companyName);

    Optional<Company> findByID(UUID companyId);

    void save(Company company);

    default List<Company> getAll() {
        return List.of();
    }

    default List<Company> getAllActive() {
        return List.of();
    }
}