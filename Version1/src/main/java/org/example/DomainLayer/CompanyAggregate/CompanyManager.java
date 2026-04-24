package org.example.DomainLayer.CompanyAggregate;

import java.util.List;

public class CompanyManager extends ICompanyMember {
    private final List<Premissions> premissions;

    public CompanyManager(String username, ICompanyMember Appointer, List<Premissions> premissions) {
        super(username, Appointer);
        this.premissions = premissions;
    }

    public List<Premissions> getPremissions() {
        return premissions;
    }

    public boolean hasPremission(Premissions premision) {
        return this.premissions.contains(premision);
    }
}
