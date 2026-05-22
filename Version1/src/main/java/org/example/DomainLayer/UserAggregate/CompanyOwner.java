package org.example.DomainLayer.UserAggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

public class CompanyOwner extends ICompanyMember {
    private final List<ICompanyMember> subordinates;

    public CompanyOwner(String username, CompanyOwner Appointer) {
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
            List<ICompanyMember> reassignedSubordinates = new ArrayList<>(companyOwner.getSubordinates());
            for (ICompanyMember sub : reassignedSubordinates) {
                this.addSubordinate(sub);
            }
            companyOwner.getSubordinates().clear();
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

    @Override
    public void buildMermaid(StringBuilder sb) {
        appendMermaidNode(sb);
        for (ICompanyMember subordinate : subordinates) {
            subordinate.buildMermaid(sb);
        }
    }

    @Override
    public CompanyFounder getFounder() {
        if (getAppointer() == null) {
            throw new IllegalStateException("Owner without appointer cannot determine founder, invalid state");
        }
        return getAppointer().getFounder();
    }
    
    public List<UUID> getEventsUnderMe() {
        List<UUID> eventsUnderMe = new ArrayList<>(getEventsIds());
        for (ICompanyMember subordinate : subordinates) {
            eventsUnderMe.addAll(subordinate.getEventsUnderMe());
        }
        return eventsUnderMe;
    }

    @Override
    public String isMyEvent(UUID eventId)
    {
        for(UUID evnId : this.getEventsIds())
        {
            if(evnId.equals(eventId))
                return this.getUsername();
        }
        
        for(ICompanyMember sub : subordinates)
        {
            return sub.isMyEvent(eventId);
        }

        return null;
    }
}
