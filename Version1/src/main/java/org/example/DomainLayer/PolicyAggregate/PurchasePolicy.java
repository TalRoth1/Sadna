package org.example.DomainLayer.PolicyAggregate;
import org.example.DomainLayer.DomainException;

public class PurchasePolicy {
    private int minTicketsPerPurchase;
    private int maxTicketsPerPurchase;
    private int minimumAge;

    public PurchasePolicy() {
        this.minTicketsPerPurchase = 1;
        this.maxTicketsPerPurchase = Integer.MAX_VALUE;
        this.minimumAge = 0;
    }

    public void validateTicketAmount(int amount) {
        if (amount < minTicketsPerPurchase) {
            throw new DomainException("amount of tickets is lower than the minimum required");
        }

        if (amount > maxTicketsPerPurchase) {
            throw new DomainException("amount of tickets is higher than the maximum allowed");
        }
    }

    public void validateMemberAge(int age) {
        if (age < minimumAge) {
            throw new DomainException("age of the member is not suitable for the policy");
        }
    }
}
