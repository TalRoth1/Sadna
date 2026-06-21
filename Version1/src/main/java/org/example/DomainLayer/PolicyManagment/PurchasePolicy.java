package org.example.DomainLayer.PolicyManagment;

import java.util.UUID;
import java.util.Objects;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;


public class PurchasePolicy {

    private IPurchaseRule rules = null;

    public IPurchaseRule getRulesView() {
        return rules;
    }

    public void addRule(IPurchaseRule rule, boolean andOr) {
        if (this.rules == null)
            this.rules = rule;
        else
            this.rules = new PurchaseComposite(rules, rule, andOr);
    }

    public void removeRule(UUID ruleId) {
        if (rules == null) {
            return;
        }

        if (Objects.equals(ruleId, rules.getId())) {
            rules = null;
        } else if (rules instanceof PurchaseComposite)
            rules = ((PurchaseComposite) rules).removeRule(ruleId);
    }

    public boolean validate(ActivePurchase purchase, User user, Event event) {
        if (this.rules == null)
            return true;
        return rules.doesHold(purchase, user, event);
    }

    /**
     * Enforce the policy: throws a {@link DomainException} when the purchase
     * violates the rules, with a human-readable explanation of the requirement
     * (V2 req 3.5). Unlike {@link #validate}, the result is never silently
     * dropped — callers must use this at checkout.
     */
    public void validateOrThrow(ActivePurchase purchase, User user, Event event) {
        if (this.rules == null) {
            return;
        }
        if (!rules.doesHold(purchase, user, event)) {
            throw new DomainException(
                    "Purchase not allowed by the purchase policy. Requirement: " + describe(rules));
        }
    }

    private static String describe(IPurchaseRule rule) {
        if (rule instanceof PurchaseComposite composite) {
            String op = composite.isAnd() ? " and " : " or ";
            return "(" + describe(composite.getLeftRule()) + op + describe(composite.getRightRule()) + ")";
        }
        if (rule instanceof AgeRule ageRule) {
            return "minimum age " + (int) ageRule.getMinAge();
        }
        if (rule instanceof MinTicketRule minRule) {
            return "at least " + minRule.getMinTicket() + " ticket(s) per order";
        }
        if (rule instanceof MaxTicketRule maxRule) {
            return "at most " + maxRule.getMaxTicket() + " ticket(s) per order";
        }
        if (rule instanceof LoneSeatRule) {
            return "no isolated single seat may be left empty";
        }
        return "purchase constraints must be met";
    }
}
