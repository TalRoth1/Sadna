package org.example.DomainLayer.CompanyAggregate;

import java.util.ArrayList;
import java.util.List;

public class CompanyOwner extends ICompanyMember {
    private final List<ICompanyMember> subordinates;

    public CompanyOwner(String username, ICompanyMember Appointer) {
        super(username, Appointer);
        this.subordinates = new ArrayList<>();
    }

    public List<ICompanyMember> getSubordinates() {
        return subordinates;
    }

    public void addSubordinate(ICompanyMember subordinate) {
        this.subordinates.add(subordinate);
        subordinate.setAppointer(this);
    }

    public void removeSubordinate(ICompanyMember subordinate) {
        // first we have to check if the subordinate is a manager or owner
        if (subordinate instanceof CompanyOwner companyOwner) {
            // if it's a Owner we need to realocate all his subordinates to the current owner
            for (ICompanyMember sub : companyOwner.getSubordinates()) {
                this.addSubordinate(sub);
            }
        }
        this.subordinates.remove(subordinate);
    }
}
