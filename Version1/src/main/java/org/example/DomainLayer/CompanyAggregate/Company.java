package org.example.DomainLayer.CompanyAggregate;

import java.util.List;
import java.util.Map;

import org.example.DomainLayer.PolicyAggregate.DiscountPolicy;
import org.example.DomainLayer.PolicyAggregate.PurchasePolicy;

public class Company {
    private int id;
    static private int idCounter = 0;
    private String name;
    private CompanyFounder founder; 
    private Map<String, ICompanyMember> members;
    private DiscountPolicy discountPolicy;
    private PurchasePolicy purchasePolicy;
    private int rating;
    private int amountRated;
    private List<Integer> eventIds;

    public Company(String founderUsername, String companyName)
    {
        this.id = Company.idCounter;
        Company.idCounter++;
        this.name = companyName;
        this.founder = new CompanyFounder(founderUsername);
        members.put(founderUsername, founder);
        this.discountPolicy = new DiscountPolicy();
        this.purchasePolicy = new PurchasePolicy();
    }

    public int getId()
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
}
