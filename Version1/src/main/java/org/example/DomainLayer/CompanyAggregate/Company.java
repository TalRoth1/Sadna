package org.example.DomainLayer.CompanyAggregate;

import org.example.DomainLayer.EventAggregate.PurchasePolicy;


public class Company
{
    private int companyID;
    private PurchasePolicy purchasePolicy;

    public Company(int companyID)
    {
        this.companyID = companyID;
    }
    public int getCompanyID() {
        return companyID;
    }

    public PurchasePolicy getPurchasePolicy()
    {
        return purchasePolicy;
    }
}
