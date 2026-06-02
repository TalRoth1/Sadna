package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

import java.util.Objects;
import java.util.UUID;

public class PurchaseComposite implements IPurchaseRule {

    private final UUID id = UUID.randomUUID();
    private IPurchaseRule leftRule;
    private IPurchaseRule rightRule;
    private boolean operator; // True = And, False = Or

    public PurchaseComposite(IPurchaseRule leftRule, IPurchaseRule rightRule, boolean operator)
    {
        this.leftRule = Objects.requireNonNull(leftRule);
        this.rightRule = Objects.requireNonNull(rightRule);
        this.operator = Objects.requireNonNull(operator);
    }

    @Override
    public UUID getId()
    {
        return this.id;
    }

    @Override
    public boolean doesHold(ActivePurchase purchase, User user, Event event)
    {
        if (operator) {
            return leftRule.doesHold(purchase, user, event) && rightRule.doesHold(purchase, user, event);
        } else {
            return leftRule.doesHold(purchase, user, event) || rightRule.doesHold(purchase, user, event);
        }
    }


    public IPurchaseRule getLeftRule() { return leftRule; }
    public IPurchaseRule getRightRule() { return rightRule; }

    /** True iff the composition operator is AND; false means OR. */
    public boolean isAnd() { return operator; }

    public IPurchaseRule removeRule(UUID ruleId)
    {
        if (Objects.equals(ruleId, id))
            return null;
        else if (Objects.equals(leftRule.getId(), ruleId))
            return rightRule;
        else if (Objects.equals(rightRule.getId(), ruleId))
            return leftRule;
        else{
            if(leftRule instanceof PurchaseComposite)
                leftRule = ((PurchaseComposite)leftRule).removeRule(ruleId);
            if(rightRule instanceof PurchaseComposite)
                rightRule = ((PurchaseComposite)rightRule).removeRule(ruleId);
            return this;
        }
    }
}