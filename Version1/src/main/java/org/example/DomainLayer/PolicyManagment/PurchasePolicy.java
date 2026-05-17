package org.example.DomainLayer.PolicyManagment;

import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;


public class PurchasePolicy {

    private IPurchaseRule rules = null;

    public IPurchaseRule getRulesView() {
        return rules;
    }

    public void addRule(IPurchaseRule rule, boolean andOr)
    {
        if (this.rules == null)
            this.rules = rule;
        else
            this.rules = new PurchaseComposite(rules, rule, andOr);
    }

    public void removeRule(UUID ruleId)
    {
        if (ruleId == rules.getId())
            {
                rules = null;
            }
        else if (rules instanceof PurchaseComposite)
            rules = ((PurchaseComposite)rules).removeRule(ruleId);
    }

    public boolean validate(ActivePurchase purchase, User user, Event event)
    {
        if(this.rules == null)
            return true;
        return rules.doesHold(purchase, user, event);
    }
}
