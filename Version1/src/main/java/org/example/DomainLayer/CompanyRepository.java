package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CompanyRepository implements ICompanyRepository {

    private final Map<Integer, Company> companies = new HashMap<>();

    @Override
    public Company findByID(UUID companyId) {
        return companies.get(companyId);
    }

    @Override
    public boolean isOwner(String username, UUID companyId) {
        Company company = findByID(companyId);

        if (company == null) {
            return false;
        }

        return company.isOwner(username);
    }

}