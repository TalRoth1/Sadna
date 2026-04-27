package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;

import java.util.HashMap;
import java.util.Map;

public class CompanyRepository implements ICompanyRepository {

    private final Map<Integer, Company> companies = new HashMap<>();

    @Override
    public Company findByID(int companyId) {
        return companies.get(companyId);
    }

    @Override
    public boolean isOwner(String username, int companyId) {
        Company company = findByID(companyId);

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

}