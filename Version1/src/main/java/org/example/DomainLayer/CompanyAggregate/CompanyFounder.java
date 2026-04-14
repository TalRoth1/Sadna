package org.example.DomainLayer.CompanyAggregate;

public class CompanyFounder implements ICompanyMember{
    private String username;

    public CompanyFounder(String username)
    {
        this.username = username;
    }

    public String getUsername()
    {
        return this.username;
    }
}
