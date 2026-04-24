package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.RolesDomainService;

public class CompanyService
{
    RolesDomainService rolesDomainService;

    public void rateCompany(String userID, int companyID, int rating)
    {
        if (rating < 0 || rating > 5)
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        try
        {
            rolesDomainService.rateCompany(userID, companyID, rating);
        }
        catch (DomainException e)
        {
            //TODO
        }
    }
}
