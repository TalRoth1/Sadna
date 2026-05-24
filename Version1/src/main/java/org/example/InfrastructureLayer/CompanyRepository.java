package org.example.InfrastructureLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.ICompanyRepository;

public class CompanyRepository implements ICompanyRepository {

    private final Map<UUID, Company> companies = new ConcurrentHashMap<>();

    @Override
    public Optional<Company> findByID(UUID companyId) {
        return Optional.ofNullable(companies.get(companyId));
    }

    @Override
    public void save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("Company is required");
        }

        companies.put(company.getId(), company);
    }

     @Override
     public UUID createCompany(String founderEmail, String companyName) {
        Company newCompany = new Company(founderEmail, companyName);
        companies.put(newCompany.getId(), newCompany);
        return newCompany.getId();
     }

    @Override
    public List<Company> getAllActive() {
        List<Company> result = new ArrayList<>();
        for (Company c : companies.values()) {
            if (c.isActive()) {
                result.add(c);
            }
        }
        return result;
    }

    @Override
    public List<Company> getAll() {
        return new ArrayList<>(companies.values());
    }
}