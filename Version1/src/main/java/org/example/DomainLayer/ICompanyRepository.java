package org.example.DomainLayer;

import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;

import java.util.List;
import java.util.Optional;

public interface ICompanyRepository {
    void createCompany(String founderUsername, String companyName);
    Optional<Company> findByID(UUID companyId);
    boolean isOwner(String username, UUID companyId);
    void save(Company company);
    List<Company> getCompaniesByMember(String username);
}
