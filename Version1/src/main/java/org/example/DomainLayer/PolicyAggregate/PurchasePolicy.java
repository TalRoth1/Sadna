package org.example.DomainLayer.PolicyAggregate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.UserAggregate.User;

public class PurchasePolicy {

    private final List<IPurchaseRule> rules = new ArrayList<>();

    public List<IPurchaseRule> getRulesView() {
        return Collections.unmodifiableList(rules);
    }

    public void addRule(IPurchaseRule rule) {
        rules.add(Objects.requireNonNull(rule));
    }

    public boolean validate(ActivePurchase purchase, User user)
    {
        for (IPurchaseRule iPurchaseRule : rules) {
            if(!iPurchaseRule.doesHold(purchase, user))
                return false;
        }
        return true;
    }
}
