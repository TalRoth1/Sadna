package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;

public interface ICompanyRepository {
    void addCompany(String founderUsername, String companyName);
    Company findByID(String companyId);
}
