package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Purchase rules for an event (1:1 with {@link Event}). Composition of {@link IPurchaseRule}s.
 */
public class PurchasePolicy {

    private final List<IPurchaseRule> rules = new ArrayList<>();

    public List<IPurchaseRule> getRulesView() {
        return Collections.unmodifiableList(rules);
    }

    public void addRule(IPurchaseRule rule) {
        rules.add(Objects.requireNonNull(rule));
    }

    public void validate(ActivePurchase ap, User user, Event event)
    {
        for (int i = 0; i < rules.size(); i++)
        {
            if (!rules.get(i).doesHold(ap, user, event))
            {
                throw new DomainException("הפעולה חורגת ממדיניות הרכישה של " + rules.get(i).getClass().getSimpleName());
            }
        }
    }
}
