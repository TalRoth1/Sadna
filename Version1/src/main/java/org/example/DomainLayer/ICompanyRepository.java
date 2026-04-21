package org.example.DomainLayer;

import java.util.Optional;

import org.example.DomainLayer.CompanyAggregate.Company;

public interface ICompanyRepository {

    Company save(Company company);

    Optional<Company> findById(int companyId);
}
