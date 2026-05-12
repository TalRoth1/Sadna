package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


public class DiscountPolicy {
    private final List<IDiscountRule> discounts = new ArrayList<IDiscountRule>();

    public List<IDiscountRule> gDiscountRules()
    {
        return Collections.unmodifiableList(discounts);
    }
    public void addRule(IDiscountRule rule)
    {
        discounts.add(Objects.requireNonNull(rule));
    }

    public void removeRule(UUID id)
    {
        discounts.removeIf(rule -> rule.getId() == id);
    }

    public float applyDiscount(ActivePurchase purchase)
    {
        float price = purchase.getPrice();
        for (IDiscountRule iDiscountRule : discounts) {
            price = iDiscountRule.apply(purchase);
            purchase.setPrice(price);
        }
        return price;
    }
}
