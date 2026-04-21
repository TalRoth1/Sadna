package org.example.InfrastructureLayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.CompanyAggregate.Company;

/**
 * In-memory implementation of {@link ICompanyRepository} for development and tests.
 */
public class InMemoryCompanyRepository implements ICompanyRepository {
    private final Map<Integer, Company> companiesById = new HashMap<>();

    @Override
    public Company save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("company cannot be null");
        }
        companiesById.put(company.getId(), company);
        return company;
    }

    @Override
    public Optional<Company> findById(int companyId) {
        return Optional.ofNullable(companiesById.get(companyId));
    }
}
