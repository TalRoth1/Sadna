package org.example.InfrastructureLayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.CompanyAggregate.Company;

/**
 * In-memory implementation of {@link ICompanyRepository} for development and tests.
 */
public class InMemoryCompanyRepository implements ICompanyRepository {
    private final Map<UUID, Company> companiesById = new HashMap<>();

    @Override
    public void save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("company cannot be null");
        }
        companiesById.put(company.getId(), company);
    }

    @Override
    public Optional<Company> findByID(UUID companyId) {
        return Optional.ofNullable(companiesById.get(companyId));
    }

    @Override
    public void createCompany(String founderUsername, String companyName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createCompany'");
    }

    @Override
    public boolean isOwner(String username, UUID companyId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isOwner'");
    }

    @Override
    public List<Company> getCompaniesByMember(String username) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCompaniesByMember'");
    }
}
