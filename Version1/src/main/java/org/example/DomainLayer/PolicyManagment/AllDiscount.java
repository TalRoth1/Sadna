package org.example.DomainLayer.PolicyManagment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public class AllDiscount implements IDiscountComposite {
    private UUID id = UUID.randomUUID();
    private final List<IDiscountRule> rules = new ArrayList<>();

    public AllDiscount()
    {
    }

    @Override
    public UUID getId()
    {
        return id;
    }

    @Override
    public float apply(ActivePurchase purchase)
    {
        float Price = purchase.getPrice();
        for (IDiscountRule rule : rules) {
            Price = rule.apply(purchase);
            purchase.setPrice(Price);
        }
        return purchase.getPrice();
    }

    public void addRule(IDiscountRule rule) {
        rules.add(Objects.requireNonNull(rule));
    }

    public void removeRule(UUID id) {
        rules.removeIf(rule -> Objects.equals(rule.getId(), id));
    }

    @Override
    public float getDiscountPercent() {
        float totalDiscountPercent = 0.0f;
        for (IDiscountRule rule : rules) {
            totalDiscountPercent += rule.getDiscountPercent();
        }
        return totalDiscountPercent;
    }

    @Override
    public List<IDiscountRule> getRules() {
        return new ArrayList<>(rules);
    }
}
