package org.example.DomainLayer.UserAggregate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

public class CompanyManager extends ICompanyMember {
    private Set<CompanyPermission> premissions;

    public CompanyManager(String username, CompanyOwner Appointer, Set<CompanyPermission> premissions) {
        super(username, Appointer);
        this.premissions = new HashSet<>(premissions);
    }

    public void setNewPremissions(Set<CompanyPermission> newPremissions) {
        this.premissions = new HashSet<>(newPremissions);
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

    @Override
    public void buildMermaid(StringBuilder sb) {
        sb.append(mermaidId()).append("[");
        sb.append('"').append(getUsername());
        if (premissions != null && !premissions.isEmpty()) {
            sb.append("\\nPerms:").append(premissions.toString());
        }
        sb.append('"').append("]\n");
        if (getAppointer() != null) {
            sb.append(getAppointer().mermaidId()).append(" --> ").append(mermaidId()).append("\n");
        }
    }

    @Override
    public CompanyFounder getFounder() {
        if (getAppointer() == null) {
            throw new IllegalStateException("Manager without appointer cannot determine founder, invalid state");
        }
        return getAppointer().getFounder();
    }
    
    public List<UUID> getEventsUnderMe() {
        return getEventsIds();
    }
}
