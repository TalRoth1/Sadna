package org.example.DomainLayer.CompanyAggregate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.PolicyAggregate.AgeRule;
import org.example.DomainLayer.PolicyAggregate.ConditionalDiscount;
import org.example.DomainLayer.PolicyAggregate.CouponCode;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PolicyAggregate.DiscountPolicy;
import org.example.DomainLayer.PolicyAggregate.LoneSeatRule;
import org.example.DomainLayer.PolicyAggregate.MaxTicketRule;
import org.example.DomainLayer.PolicyAggregate.MinTicketRule;
import org.example.DomainLayer.PolicyAggregate.OvertDiscount;
import org.example.DomainLayer.PolicyAggregate.PurchasePolicy;
import org.example.DomainLayer.Rating;

public class Company {
    private final UUID id;
    private CompanyFounder founder;
    private String name; 
    private final Map<String, ICompanyMember> members;
    private final Map<UUID, Invitation> invitations;
    private final DiscountPolicy discountPolicy;
    private final PurchasePolicy purchasePolicy;
    private double rating;
    private int amountRated;
    private final List<UUID> eventIds;
    private Map<UUID, Rating> ratingsByUsers;
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
    this.ratingsByUsers = new HashMap<UUID, Rating>();
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

    public double getRating()
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
        if (!isCompanyMember(appointerUsername) || !(members.get(appointerUsername) instanceof CompanyOwner))
        {
            throw new IllegalArgumentException("The appointer is not a company owner and therefore cannot invite a new owner");
        }
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

    public boolean inviteNewManager(String appointeeUsername, String appointerUsername, Set<CompanyPermission> premissions)
    {
        if (!isCompanyMember(appointerUsername) || !(members.get(appointerUsername) instanceof CompanyOwner))
        {
            throw new IllegalArgumentException("The appointer is not a company owner and therefore cannot invite a new manager");
        }
        if (isCompanyMember(appointeeUsername))
        {
            throw new IllegalArgumentException("The appointee is already a member of the company and therefore cannot be invited as a manager");
        }
        Invitation invitation = new ManagerInvetation(appointerUsername, appointeeUsername, id, premissions);
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
        if (invitation instanceof OwnerInvetation ownerInvitation)
        {
            return appointNewOwner(ownerInvitation.getAppointeeUsername(), ownerInvitation.getAppointerUsername());
        }
        else if (invitation instanceof ManagerInvetation managerInvitation)
        {
            return appointNewManager(managerInvitation.getAppointeeUsername(), managerInvitation.getAppointerUsername(), managerInvitation.getPremissions());
        }
        return false;
    }

    public boolean appointNewManager(String appointeeUsername, String appointerUsername, Set<CompanyPermission> premissions)
    {
        if (!isCompanyMember(appointerUsername) || !(members.get(appointerUsername) instanceof CompanyOwner))
        {
            throw new IllegalArgumentException("The appointer is not a company owner and therefore cannot appoint a new manager");
        }
        if (isCompanyMember(appointeeUsername))
        {
            throw new IllegalArgumentException("The appointee is already a member of the company and therefore cannot be appointed as a manager");
        }
        CompanyManager newManager = new CompanyManager(appointeeUsername, (CompanyOwner) members.get(appointerUsername), premissions);
        members.put(appointeeUsername, newManager);
        return true;
    }
    public boolean appointNewOwner(String appointeeUsername, String appointerUsername)
    {
        if (!isCompanyMember(appointerUsername) || !(members.get(appointerUsername) instanceof CompanyOwner))
        {
            throw new IllegalArgumentException("The appointer is not a company owner and therefore cannot appoint a new owner");
        }
        CompanyOwner appointer = (CompanyOwner) members.get(appointerUsername);
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
            CompanyOwner appointeeManager = (CompanyOwner) members.get(appointee.getAppointer().getUsername());
            appointeeManager.removeSubordinate(appointee);
            appointer.addSubordinate(appointee);
            return true;
        }
        CompanyOwner newOwner = new CompanyOwner(appointeeUsername, (CompanyOwner) members.get(appointerUsername));
        members.put(appointeeUsername, newOwner);
        return true;
    }

    public boolean hasPremision(String username, CompanyPermission premision, UUID eventId)
    {
        if (!isCompanyMember(username))
        {
            return false;
        }
        return members.get(username).hasPremission(premision, eventId);
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

    public void AdminClose() {
        if (!isActive) {
            throw new IllegalStateException("Company is already inactive");
        }

        isActive = false;

        members.entrySet().removeIf(entry ->
                entry.getValue() instanceof CompanyOwner
                        || entry.getValue() instanceof CompanyManager
        );
    }

    public void FounderClose(String founderUsername) {
        // TODO: imlement founder close
    }

    public boolean hasMember(String username) {
        return username != null && members.containsKey(username);
    }

    // differ from removeMemberAsOwner in the fact that the admin could remove any member of the company without any restriction, while the founder can only remove members that are under him in the company hyrarchy and he cannot remove managers that are not under him.
    public void removeMemberAsAdmin(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (!members.containsKey(username)) {
            throw new IllegalArgumentException("User is not a company member");
        }
        removeMember(username);
    }

    public void removeMemberAsOwner(String username, String ownerUsername) {
        if (username == null || username.isBlank() || ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Username and owner username are required");
        }
        if (!members.containsKey(username)) {
            throw new IllegalArgumentException("User is not a company member");
        }
        if (!members.containsKey(ownerUsername) || !(members.get(ownerUsername) instanceof CompanyOwner)) {
            throw new IllegalArgumentException("Owner username is not a company owner");
        }
        ICompanyMember memberToRemove = members.get(username);
        if (memberToRemove instanceof CompanyManager manager) {
            if (!manager.isSubordinateOf(ownerUsername)) {
                throw new IllegalArgumentException("The owner can only remove managers that are under him in the company hyrarchy");
            }
        } else if (memberToRemove instanceof CompanyOwner owner) {
            if (!owner.isSubordinateOf(ownerUsername)) {
                throw new IllegalArgumentException("The owner can only remove owners that are under him in the company hyrarchy");
            }
        } else {
            throw new IllegalArgumentException("The owner can only remove managers or owners");
        }
        removeMember(username);
    }

    private void removeMember(String username) {
        ICompanyMember memberToRemove = members.get(username);
        memberToRemove.removeFromCompanyHyrarchy();
        members.remove(username);
    }

    public void addPurchasePolicy(Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat)
    {
        if(Optional.ofNullable(age).isPresent())
            this.purchasePolicy.addRule(new AgeRule(age.get()));
        if(Optional.ofNullable(minTicket).isPresent())
            this.purchasePolicy.addRule(new MinTicketRule(minTicket.get()));
        if(Optional.ofNullable(maxTicket).isPresent())
            this.purchasePolicy.addRule(new MaxTicketRule(maxTicket.get()));
        if(Optional.ofNullable(allowLoneSeat).isPresent())
            this.purchasePolicy.addRule(new LoneSeatRule(allowLoneSeat.get()));

    }

    public void deletePurchaseRule(boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat)
    {
        if(age)
            this.purchasePolicy.removeRule(new AgeRule(0));
        if(minTicket)
            this.purchasePolicy.removeRule(new MinTicketRule(0));
        if(maxTicket)
            this.purchasePolicy.removeRule(new MaxTicketRule(0));
        if(allowLoneSeat)
            this.purchasePolicy.removeRule(new LoneSeatRule(false));
    }

    public void addOvertDiscount(LocalDate fromDate, LocalDate toDate, float discountPrecent)
    {
        this.discountPolicy.addRule(new OvertDiscount(discountPrecent, fromDate, toDate));
    }

    public void addConditionalDiscount(LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets)
    {
        this.discountPolicy.addRule(new ConditionalDiscount(fromDate, toDate, discountPrecent, requiredTickets, appliedTickets));
    }

    public void addCouponCode(LocalDate fromDate, LocalDate toDate, float discountPrecent, String code)
    {
        this.discountPolicy.addRule(new CouponCode(fromDate, toDate, discountPrecent, code));
    }

    public void removeDiscount(UUID discountId)
    {
        this.discountPolicy.removeRule(discountId);
    }

    public synchronized void addRating(UUID userID, int rating) {
        if (ratingsByUsers.containsKey(userID))
            throw new DomainException("User already reviewed this company");
        else {
            Rating r = new Rating(rating, userID);
            ratingsByUsers.put(userID, r);

            double sum = 0;

            for (Rating existingRating : ratingsByUsers.values()) {
                sum += existingRating.getRating();
            }

            this.rating = sum / ratingsByUsers.size();
        }
    }

    public Object getOwnerNames() {
        List<String> ownerNames = new ArrayList<>();
        for (ICompanyMember member : members.values()) {
            if (member instanceof CompanyOwner) {
                ownerNames.add(member.getUsername());
            }
        }
        return ownerNames;
    }
}
