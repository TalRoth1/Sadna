package org.example.DomainLayer.PolicyManagment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public class MaxDiscount implements IDiscountComposite {
    private final UUID id = UUID.randomUUID();
    private final List<IDiscountRule> discountRules = new ArrayList<>();

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public float apply(ActivePurchase purchase) {
        if (discountRules.isEmpty()) {
            return purchase.getPrice();
        }
        return discountRules.get(0).apply(purchase);
    }

    public void addRule(IDiscountRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule cannot be null");
        }

        int i = 0;
        while (i < discountRules.size() && discountRules.get(i).getDiscountPercent() > rule.getDiscountPercent()) {
            i++;
        }
        discountRules.add(i, rule);
    }
    
    public void removeRule(UUID id) {
        discountRules.removeIf(rule -> rule.getId().equals(id));
    }
    
    @Override
    public float getDiscountPercent() {
        if (discountRules.isEmpty()) {
            return 0.0f;
        }
        return discountRules.get(0).getDiscountPercent();
    }

    @Override
    public List<IDiscountRule> getRules() {
        return new ArrayList<>(discountRules);
    }
}