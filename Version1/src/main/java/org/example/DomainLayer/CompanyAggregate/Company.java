package org.example.DomainLayer.CompanyAggregate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.Rating;
import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.ConditionalDiscount;
import org.example.DomainLayer.PolicyManagment.CouponCode;
import org.example.DomainLayer.PolicyManagment.DiscountPolicy;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import org.example.DomainLayer.PolicyManagment.PurchasePolicy;
import org.example.DomainLayer.UserAggregate.*;


public class Company {
    private final UUID id;
    private final String founderUsername;
    private CompanyFounder founder;
    private String name;
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
    this.founderUsername = founderUsername;
    this.eventIds = new ArrayList<>();
    this.discountPolicy = new DiscountPolicy();
    this.purchasePolicy = new PurchasePolicy();
    this.isActive = true;
    this.ratingsByUsers = new HashMap<UUID, Rating>();
}

    public UUID getId()
    {
        return this.id;
    }

    public Invitation getInvitation(UUID invitationId)
    {
        return invitations.get(invitationId);
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String newName)
    {
        this.name = newName;
    }

    public String getFounderUsername()
    {
        return this.founderUsername;
    }

    public void addEvent(UUID newEventId)
    {
        eventIds.add(newEventId);
    }

    public List<UUID> getEventIds() {
        return List.copyOf(eventIds);
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

    public boolean isActive() {
        return isActive;
    }

    // assumes the members removal is handled in the domain service layer and that the company founder is not removed from the company
    public void AdminClose() {
        if (!isActive) {
            throw new IllegalStateException("Company is already inactive");
        }

        isActive = false;
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

    public void addPurchasePolicy(Float age, Integer minTicket, Integer maxTicket, Boolean allowLoneSeat) {
        if (age != null)
            this.purchasePolicy.addRule(new AgeRule(age));

        if (minTicket != null)
            this.purchasePolicy.addRule(new MinTicketRule(minTicket));

        if (maxTicket != null)
            this.purchasePolicy.addRule(new MaxTicketRule(maxTicket));

        if (allowLoneSeat != null)
            this.purchasePolicy.addRule(new LoneSeatRule(allowLoneSeat));
    }

    public void deletePurchaseRule(UUID ruleId)
    {
        purchasePolicy.removeRule(ruleId);
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
}
