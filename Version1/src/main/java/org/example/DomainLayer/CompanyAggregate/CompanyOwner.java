package org.example.DomainLayer.CompanyAggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
            // if it's a Owner we need to realocate all his subordinates to the current
            // owner
            for (ICompanyMember sub : companyOwner.getSubordinates()) {
                this.addSubordinate(sub);
            }
        }
        this.subordinates.remove(subordinate);
    }

    @Override
    public boolean hasPremission(CompanyPermission premision, UUID eventId) {
        // owner just need to be in charge of the event to have premision to do any action on it
        return isInChargeOfEvent(eventId);
    }

    @Override
    public boolean isInChargeOfEvent(UUID eventId) {
        if (getEventsIds().contains(eventId)) {
            return true;
        }
        for (ICompanyMember subordinate : subordinates) {
            if (subordinate.isInChargeOfEvent(eventId)) {
                return true;
            }
        }
        return false;
    }
}
