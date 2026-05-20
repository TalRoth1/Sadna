package org.example.DomainLayer;

import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;

import java.util.List;
import java.util.Optional;

public interface ICompanyRepository {
    Company createCompany(String founderUsername, String companyName);
    Optional<Company> findByID(UUID companyId);
    boolean isOwner(String username, UUID companyId);
    void save(Company company);
    List<Company> getCompaniesByMember(String username);
    /**Declared as a default method returning an empty
     *  list only so the build stays green while InMemoryCompanyRepository 
     * still exists (Step 3 will delete that class and override this method properly in CompanyRepository).*/
    default List<Company> getAllActive() {
        return List.of();
    }
}
