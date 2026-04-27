package org.example.DomainLayer.CompanyAggregate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.DomainLayer.PolicyAggregate.DiscountPolicy;
import org.example.DomainLayer.PolicyAggregate.PurchasePolicy;

public class Company {
    private int id;
    static private int idCounter = 0;
    private CompanyFounder founder; 
    private Map<String, ICompanyMember> members;
    private DiscountPolicy discountPolicy;
    private PurchasePolicy purchasePolicy;
    private int rating;
    private int amountRated;
    private List<Integer> eventIds;
    private boolean isActive;

    public Company(String founderUsername)
    {
        this.id = Company.idCounter;
        Company.idCounter++;
        this.members = new HashMap<>();
        this.eventIds = new ArrayList<>();
        this.founder = new CompanyFounder(founderUsername);
        members.put(founderUsername, founder);
        this.discountPolicy = new DiscountPolicy();
        this.purchasePolicy = new PurchasePolicy();
        this.isActive = true;
    }

    public int getId()
    {
        return this.id;
    }

    public CompanyFounder getFounder()
    {
        return this.founder;
    }

    public void addEvent(Integer newEventId)
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
}
