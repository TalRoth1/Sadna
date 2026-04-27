package org.example.DomainLayer;

import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;

public interface ICompanyRepository {
    Company findByID(UUID companyId);
    boolean isOwner(String username, UUID companyId);
    void save(Company company);
}
