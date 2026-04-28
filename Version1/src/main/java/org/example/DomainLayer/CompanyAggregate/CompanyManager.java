package org.example.DomainLayer.CompanyAggregate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CompanyManager extends ICompanyMember {
    private final Set<CompanyPermission> premissions;

    public CompanyManager(String username, ICompanyMember Appointer, Set<CompanyPermission> premissions) {
        super(username, Appointer);
        this.premissions = new HashSet<>(premissions);
    }

    public Set<CompanyPermission> getPremissions() {
        return premissions;
    }

    public boolean hasPremission(CompanyPermission premision) {
        return this.premissions.contains(premision);
    }
    
    @Override
    public boolean hasPremission(CompanyPermission premision, UUID eventId) {
        // manager need to have the premision and be in charge of the event to have premision to do any action on it
        return hasPremission(premision) && isInChargeOfEvent(eventId);
    }

    @Override
    public boolean isInChargeOfEvent(UUID eventId) {
        return this.getEventsIds().contains(eventId);
    }
}
