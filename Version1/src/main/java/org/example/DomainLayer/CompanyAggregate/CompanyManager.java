package org.example.DomainLayer.CompanyAggregate;

import java.util.Set;

public class CompanyManager extends ICompanyMember {
    private final Set<Premissions> premissions;

    public CompanyManager(String username, ICompanyMember Appointer, Set<Premissions> premissions) {
        super(username, Appointer);
        this.premissions = premissions;
    }

    public Set<Premissions> getPremissions() {
        return premissions;
    }

    public boolean hasPremission(Premissions premision) {
        return this.premissions.contains(premision);
    }
}
