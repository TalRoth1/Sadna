package org.example.DomainLayer.CompanyAggregate;

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

    public int getRating()
    {
        return this.rating;
    }

    public void updateRating(int rating)
    {
        this.rating = ((this.rating * this.amountRated) + rating)/ (amountRated + 1);
        this.amountRated ++; 
    }

    // Appointing a new owner to the company.
    public boolean appointNewOwner(String appointeeUsername, String appointerUsername)
    {
        // check if appointer is a in the company and is an owner
        if (!members.containsKey(appointerUsername) || !(members.get(appointerUsername) instanceof CompanyOwner))
        {
            return false;
        }
        CompanyOwner appointer = (CompanyOwner) members.get(appointerUsername);
        // check if appointee is already a member of the company, if so we have to check that he under the appointer and that he's a manager, otherwise we can appoint him as an owner without any problem
        if (members.containsKey(appointeeUsername))
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
}
