package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DiscountPolicy {
    private final IDiscountComposite discount;


    public DiscountPolicy(DiscountType discountType) {
        switch (discountType) {
            case MAX:
                this.discount = new MaxDiscount();
                break;
            case ALL:
                this.discount = new AllDiscount();
                break;
            default:
                throw new IllegalArgumentException("Unknown discount type");
        }
    }
    public void addRule(IDiscountRule rule) {
        discount.addRule(Objects.requireNonNull(rule));
    }

    public void removeRule(UUID id) {
        discount.removeRule(id);
    }


    public float applyDiscount(ActivePurchase purchase) 
    {
        if (purchase == null) {
            throw new IllegalArgumentException("Active purchase is required");
        }

        float basePrice = purchase.getTicketIDs().values().stream().reduce(0.0f, Float::sum);
        purchase.setPrice(basePrice);


        return discount.apply(purchase);
    }

    public boolean hasRules() {
        return discount != null;
    }

    public List<IDiscountRule> getDiscountRules() {
        return discount.getRules();
    }
}