package org.example.DomainLayer;

import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;

import java.util.List;

public interface ICompanyRepository {
<<<<<<< HEAD
    Company findByID(UUID companyId);
    boolean isOwner(String username, UUID companyId);
    void save(Company company);
    List<Company> getCompaniesByMember(String username);
=======
    void addCompany(String founderUsername, String companyName);
    Company findByID(String companyId);
>>>>>>> 25e1b03cdd3fcd72bbd4ef158cdc419187a7fa9b
}
