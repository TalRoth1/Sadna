package org.example.DomainLayer.CompanyAggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class ICompanyMember {
    private final String username;
    private CompanyOwner Appointer;
    private final List<UUID> eventsIds;

    public String getUsername() {
        return username;
    }

    public ICompanyMember getAppointer() {
        return Appointer;
    }

    public List<UUID> getEventsIds() {
        return eventsIds;
    }

    public abstract List<UUID> getEventsUnderMe();

    public ICompanyMember(String username, CompanyOwner Appointer) {
        this.username = username;
        this.Appointer = Appointer;
        this.eventsIds = new ArrayList<>();
    }

    public boolean isSubordinateOf(String username) {
        if (this.Appointer == null) {
            return false;
        }
        if (this.Appointer.getUsername().equals(username)) {
            return true;
        }
        return this.Appointer.isSubordinateOf(username);
    }

    public void setAppointer(CompanyOwner newAppointer) {
        this.Appointer = newAppointer;
    }

    public abstract boolean hasPremission(CompanyPermission premision, UUID eventId);

    public abstract boolean isInChargeOfEvent(UUID eventId);

    public void removeFromCompanyHyrarchy(){
        if (Appointer == null) {
            throw new IllegalStateException("Cannot remove the founder from the company hyrarchy, try closeing the company instead");
        }
        // Move all events to the parent first, then clear the local ownership list.
        List<UUID> transferredEvents = new ArrayList<>(eventsIds);
        Appointer.getEventsIds().addAll(transferredEvents);
        eventsIds.clear();
        // Potantially here we also send a notification to the event that the manager in charge of it has changed.
        Appointer.removeSubordinate(this);
        this.setAppointer(null);
    }

}
