package org.example.DomainLayer.CompanyAggregate;

import java.util.List;
import java.util.Map;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PolicyAggregate.DiscountPolicy;
import org.example.DomainLayer.PolicyAggregate.PurchasePolicy;
import org.example.DomainLayer.Rating;

public class Company {
    private int id;
    static private int idCounter = 0;
    private CompanyFounder founder; 
    private Map<String, ICompanyMember> members;
    private DiscountPolicy discountPolicy;
    private PurchasePolicy purchasePolicy;
    private double rating;
    private int amountRated;
    private List<Integer> eventIds;

    Map<String, Rating> ratingsByUsers = new java.util.LinkedHashMap<>();

    public Company(String founderUsername)
    {
        this.id = Company.idCounter;
        Company.idCounter++;
        this.founder = new CompanyFounder(founderUsername);
        members.put(founderUsername, founder);
        this.discountPolicy = new DiscountPolicy();
        this.purchasePolicy = new PurchasePolicy();
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

    public double getRating()
    {
        return this.rating;
    }

    public void updateRating(int rating)
    {
        this.rating = ((this.rating * this.amountRated) + rating)/ (amountRated + 1);
        this.amountRated ++; 
    }

    public synchronized void addRating(String userID, int rating) {
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
