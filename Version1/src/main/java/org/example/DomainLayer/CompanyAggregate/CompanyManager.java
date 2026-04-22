package org.example.DomainLayer.CompanyAggregate;

import java.util.List;

public class CompanyManager extends ICompanyMember {
    private List<Premissions> premissions;

    public CompanyManager(String username, ICompanyMember Appointer, List<Premissions> premissions) {
        super(username, Appointer);
        this.premissions = premissions;
    }
}
