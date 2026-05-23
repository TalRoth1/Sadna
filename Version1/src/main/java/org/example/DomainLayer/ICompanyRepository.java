package org.example.DomainLayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;

public interface ICompanyRepository {
    UUID createCompany(String founderEmail, String companyName);
    Optional<Company> findByID(UUID companyId);
    void save(Company company);
    /**Declared as a default method returning an empty
     *  list only so the build stays green while InMemoryCompanyRepository 
     * still exists (Step 3 will delete that class and override this method properly in CompanyRepository).*/
    default List<Company> getAllActive() {
        return List.of();
    }
}
