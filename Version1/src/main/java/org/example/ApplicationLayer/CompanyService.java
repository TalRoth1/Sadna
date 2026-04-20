package org.example.ApplicationLayer;

import org.example.DomainLayer.ICompanyRepository;

public class CompanyService {
    private ICompanyRepository repo;
    
    public void createCompany(String founderUsername, String CompanyName)
    {
        repo.addCompany(founderUsername, CompanyName);
    }
}
