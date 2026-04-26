package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;

public interface ICompanyRepository {
    Company findByID(int companyId);
}
