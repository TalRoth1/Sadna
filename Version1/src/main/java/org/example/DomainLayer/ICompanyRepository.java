package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;

import java.util.List;

public interface ICompanyRepository {
    Company findByID(int companyId);
    boolean isOwner(String username, int companyId);
    void save(Company company);
    List<Company> getCompaniesByMember(String username);
}
