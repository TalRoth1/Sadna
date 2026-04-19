package org.example.DomainLayer.CompanyAggregate;

import org.example.DomainLayer.EventAggregate.PurchasePolicy;


public class Company
{
    private String companyID;
    private PurchasePolicy purchasePolicy;

    public Company(String companyID)
    {
        this.companyID = companyID;
    }
    public String getCompanyID() {
        return companyID;
    }

    public PurchasePolicy getPurchasePolicy()
    {
        return purchasePolicy;
    }
}
