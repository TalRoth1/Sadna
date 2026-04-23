package org.example.DomainLayer.PolicyAggregate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public class PurchasePolicy {

    private final List<IPurchaseRule> rules = new ArrayList<>();

    public List<IPurchaseRule> getRulesView() {
        return Collections.unmodifiableList(rules);
    }

    public void addRule(IPurchaseRule rule) {
        rules.add(Objects.requireNonNull(rule));
    }

    public void validate(ActivePurchase purchase)
    {
        //TODO
    }
}
