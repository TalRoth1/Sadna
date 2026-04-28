package org.example.DomainLayer.PolicyAggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.UserAggregate.User;

public class PurchasePolicy {

    private final List<IPurchaseRule> rules = new ArrayList<>();

    public List<IPurchaseRule> getRulesView() {
        return rules;
    }

    public void addRule(IPurchaseRule rule)
    {
        Objects.requireNonNull(rule);
        rules.removeIf(existingRule -> existingRule.getClass().equals(rule.getClass()));
        rules.add(rule);
    }

    public void removeRule(IPurchaseRule ruleType)
    {
        Objects.requireNonNull(ruleType);
        rules.removeIf(existingRule -> existingRule.getClass().equals(ruleType.getClass()));
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
