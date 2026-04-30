package org.example.DomainLayer.Repositories;

import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.CompanyAggregate.Company;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CompanyRepository implements ICompanyRepository {

    private final Map<UUID, Company> companies = new HashMap<>();

    @Override
    public Optional<Company> findByID(UUID companyId) {
        return Optional.ofNullable(companies.get(companyId));
    }

    @Override
    public boolean isOwner(String username, UUID companyId) {
        Company company = findByID(companyId).get();

        if (company == null) {
            return false;
        }

        return company.isOwner(username);
    }

    @Override
    public void save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("Company is required");
        }

        companies.put(company.getId(), company);
    }

     @Override
    public List<Company> getCompaniesByMember(String username) {
        List<Company> result = new ArrayList<>();

        for (Company company : companies.values()) {
            if (company.hasMember(username)) {
                result.add(company);
            }
        }

        return result;
    }

     @Override
     public void createCompany(String founderUsername, String companyName) {
        Company newCompany = new Company(founderUsername, companyName);
        companies.put(newCompany.getId(), newCompany);
     }
}