package org.example.DomainLayer.CompanyAggregate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.example.DomainLayer.PolicyAggregate.DiscountPolicy;
import org.example.DomainLayer.PolicyAggregate.PurchasePolicy;

public class Company {
    private UUID id;
    private CompanyFounder founder;
    private String name; 
    private Map<String, ICompanyMember> members;
    private Map<UUID, Invitation> invitations;
    private DiscountPolicy discountPolicy;
    private PurchasePolicy purchasePolicy;
    private int rating;
    private int amountRated;
    private List<UUID> eventIds;
    private boolean isActive;

public Company(String founderUsername, String name) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.members = new HashMap<>();
    this.invitations = new HashMap<>();
    this.eventIds = new ArrayList<>();
    this.founder = new CompanyFounder(founderUsername);
    this.members.put(founderUsername, founder);
    this.discountPolicy = new DiscountPolicy();
    this.purchasePolicy = new PurchasePolicy();
    this.isActive = true;
}

    public UUID getId()
    {
        return this.id;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String newName)
    {
        this.name = newName;
    }

    public CompanyFounder getFounder()
    {
        return this.founder;
    }

    public void addEvent(UUID newEventId)
    {
        eventIds.add(newEventId);
    }

    public DiscountPolicy getDiscountPolicy()
    {
        return this.discountPolicy;
    }

    public PurchasePolicy getPurchasePolicy()
    {
        return this.purchasePolicy;
    }

    public int getRating()
    {
        return this.rating;
    }

    public void updateRating(int rating)
    {
        this.rating = ((this.rating * this.amountRated) + rating)/ (amountRated + 1);
        this.amountRated ++; 
    }

    private boolean isCompanyMember(String username)
    {
        return members.containsKey(username);
    }

    public boolean inviteNewOwner(String appointeeUsername, String appointerUsername)
    {
        // check if appointer is a in the company and is an owner
        if (!isCompanyMember(appointerUsername) || !(members.get(appointerUsername) instanceof CompanyOwner))
        {
            throw new IllegalArgumentException("The appointer is not a company owner and therefore cannot invite a new owner");
        }
        // check if appointee is already a member of the company, if so we have to check that he under the appointer and that he's a manager, otherwise we can appoint him as an owner without any problem
        if (isCompanyMember(appointeeUsername))
        {
            if (!members.get(appointeeUsername).isSubordinateOf(appointerUsername))
            {
                throw new IllegalArgumentException("The appointee is a manager but is not a subordinate of the appointer and therefore cannot be appointed as an owner by him/her");
            }
            if (!(members.get(appointeeUsername) instanceof CompanyManager))
            {
                throw new IllegalArgumentException("The appointee is a company owner and cannot be appointed as an owner again");
            }
        }
        Invitation invitation = new OwnerInvetation(appointerUsername, appointeeUsername, id);
        invitations.put(invitation.getId(), invitation);
        return true;
    }

    public boolean acceptInvitation(UUID invitationId)
    {
        if (!invitations.containsKey(invitationId))
        {
            throw new IllegalArgumentException("The invitation does not exist");
        }
        Invitation invitation = invitations.get(invitationId);
        if (invitation instanceof OwnerInvetation)
        {
            return appointNewOwner(invitation.getAppointeeUsername(), invitation.getAppointerUsername());
        }
        else{
            // NOT IMPLEMENTED YET
        }
        return false;
    }

    // Appointing a new owner to the company.
    public boolean appointNewOwner(String appointeeUsername, String appointerUsername)
    {
        // check if appointer is a in the company and is an owner
        if (!isCompanyMember(appointerUsername) || !(members.get(appointerUsername) instanceof CompanyOwner))
        {
            throw new IllegalArgumentException("The appointer is not a company owner and therefore cannot appoint a new owner");
        }
        CompanyOwner appointer = (CompanyOwner) members.get(appointerUsername);
        // check if appointee is already a member of the company, if so we have to check that he under the appointer and that he's a manager, otherwise we can appoint him as an owner without any problem
        if (isCompanyMember(appointeeUsername))
        {
            if (!members.get(appointeeUsername).isSubordinateOf(appointerUsername))
            {
                throw new IllegalArgumentException("The appointee is a manager but is not a subordinate of the appointer and therefore cannot be appointed as an owner by him/her");
            }
            if (!(members.get(appointeeUsername) instanceof CompanyManager))
            {
                throw new IllegalArgumentException("The appointee is a company owner and cannot be appointed as an owner again");
            }
            CompanyManager appointee = (CompanyManager) members.get(appointeeUsername);
            // remove the appointee from his current manager's subordinates list
            CompanyOwner appointeeManager = (CompanyOwner) members.get(appointee.getAppointer().getUsername());
            appointeeManager.removeSubordinate(appointee);
            appointer.addSubordinate(appointee);
            return true;
        }
        // create new company owner in case the appointee is not a member of the company
        CompanyOwner newOwner = new CompanyOwner(appointeeUsername, members.get(appointerUsername));
        members.put(appointeeUsername, newOwner);
        return true;
    }

    public boolean isOwner(String username) {
    if (username == null || username.isBlank()) {
        return false;
    }

    ICompanyMember member = members.get(username);

    return member instanceof CompanyOwner
            || member instanceof CompanyFounder;
    }

    public boolean isActive() {
        return isActive;
    }

    public void close() {
        if (!isActive) {
            throw new IllegalStateException("Company is already inactive");
        }

        isActive = false;

        members.entrySet().removeIf(entry ->
                entry.getValue() instanceof CompanyOwner
                        || entry.getValue() instanceof CompanyManager
        );
    }

    public boolean hasMember(String username) {
        return username != null && members.containsKey(username);
    }

    public void removeMember(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (!members.containsKey(username)) {
            throw new IllegalArgumentException("User is not a company member");
        }

        members.remove(username);
    }
}
