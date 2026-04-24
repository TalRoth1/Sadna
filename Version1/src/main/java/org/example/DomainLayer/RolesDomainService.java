package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;

public class RolesDomainService
{
    ICompanyRepository companyRepository;

    public void rateCompany(String userID, int companyID, int rating)
    {
        Company company = companyRepository.findByID(companyID);

        if (company == null)
            throw new DomainException("Event not found while rating");

        if (userID == null || userID.isBlank())
            throw new DomainException("User not found while rating");

        company.addRating(userID, rating);
        companyRepository.save(company);
    }
}
