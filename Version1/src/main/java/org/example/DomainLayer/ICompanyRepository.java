package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;

public interface ICompanyRepository {
    Company findByID(int companyId);
    boolean isOwner(String username, int companyId);
    void save(Company company);
}
