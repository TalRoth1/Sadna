package org.example.DomainLayer.CompanyAggregate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.ConditionalDiscount;
import org.example.DomainLayer.PolicyManagment.CouponCode;
import org.example.DomainLayer.PolicyManagment.DiscountPolicy;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import org.example.DomainLayer.PolicyManagment.PurchasePolicy;
import org.example.DomainLayer.Rating;


public class Company {
    public enum CompanyStatus {
        ACTIVE,
        SUSPENDED,
        CLOSED
    }

    private final UUID id;
    private final String founderEmail;
    private String name; 
    private final DiscountPolicy discountPolicy;
    private final PurchasePolicy purchasePolicy;
    private double rating;
    private int amountRated;
    private final List<UUID> eventIds;
    private final Map<UUID, Rating> ratingsByUsers;
    private CompanyStatus status;
    private String founderName;

public Company(String founderEmail, String name) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.founderEmail = founderEmail;
    this.eventIds = new ArrayList<>();
    this.discountPolicy = new DiscountPolicy();
    this.purchasePolicy = new PurchasePolicy();
    this.status = CompanyStatus.ACTIVE;
    this.ratingsByUsers = new HashMap<>();
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

    public String getFounderEmail()
    {
        return this.founderEmail;
    }

    public String getFounderUsername() {
        return founderName;
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

    public CompanyStatus getStatus() {
        return status;
    }

    public void updateRating(int rating)
    {
        this.rating = ((this.rating * this.amountRated) + rating)/ (amountRated + 1);
        this.amountRated ++; 
    }

    public boolean isActive() {
        return status == CompanyStatus.ACTIVE;
    }

    // assumes the members removal is handled in the domain service layer and that the company founder is not removed from the company
    public void AdminClose() {
        if (status == CompanyStatus.CLOSED) {
            throw new IllegalStateException("Company is already inactive");
        }

        status = CompanyStatus.CLOSED;
    }

    public void FounderClose(String founderEmail) {
        // TODO: imlement founder close
    }

    public void addPurchasePolicy(Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat, boolean andOr)
    {
    if (age != null && age.isPresent()) 
        this.purchasePolicy.addRule(new AgeRule(age.get()), andOr);
        
    if (minTicket != null && minTicket.isPresent()) 
        this.purchasePolicy.addRule(new MinTicketRule(minTicket.get()), andOr);
        
    if (maxTicket != null && maxTicket.isPresent()) 
        this.purchasePolicy.addRule(new MaxTicketRule(maxTicket.get()), andOr);
        
    if (allowLoneSeat != null && allowLoneSeat.isPresent()) 
        this.purchasePolicy.addRule(new LoneSeatRule(allowLoneSeat.get()), andOr);
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
