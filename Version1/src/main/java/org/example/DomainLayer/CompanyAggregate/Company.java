package org.example.DomainLayer.CompanyAggregate;

import java.util.List;

import org.example.DomainLayer.EventAggregate.Event;

public class Company {
    private int id;
    static private int idCounter = 0;
    private CompanyFounder founder; 
    private List<ICompanyMember> members;
    private DiscountPolicy discountPolicy;
    private PurchasePolicy purchasePolicy;
    private int rating;
    private int amountRated;
    private List<Event> events;

    public Company(String founderUsername)
    {
        this.id = Company.idCounter;
        Company.idCounter++;
        this.founder = new CompanyFounder(founderUsername);
        members.add(founder);
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

    public void addEvent(Event newEvent)
    {
        events.add(newEvent);
    }

    public Event getEvent(int EventId)
    {
        for (Event event : events) {
            if(event.getId() == EventId)
                {
                    return event;
                }
        }
        return null;
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
