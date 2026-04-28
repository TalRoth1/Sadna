package org.example.DomainLayer.CompanyAggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class ICompanyMember {
    private final String username;
    private ICompanyMember Appointer;
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

    public ICompanyMember(String username, ICompanyMember Appointer) {
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

    public void setAppointer(ICompanyMember newAppointer) {
        this.Appointer = newAppointer;
    }

    public abstract boolean hasPremission(CompanyPermission premision, UUID eventId);

    public abstract boolean isInChargeOfEvent(UUID eventId);

}
