package org.example.DomainLayer.PolicyAggregate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

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
