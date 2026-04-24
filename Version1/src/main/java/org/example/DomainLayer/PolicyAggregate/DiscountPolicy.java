package org.example.DomainLayer.PolicyAggregate;

import java.util.ArrayList;
import java.util.List;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public class DiscountPolicy {
    private final List<IDiscountRule> discounts = new ArrayList<IDiscountRule>();

    public void addRule(IDiscountRule rule)
    {
        discounts.add(rule);
    }

    public boolean applyDiscount(ActivePurchase purchase)
    {
        for (IDiscountRule iDiscountRule : discounts) {
            if(!iDiscountRule.apply(purchase))
                {
                    return false;
                }
        }
        return true;
    }
}
