package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.EventAggregate.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Discount rules for an event (1:1 with {@link Event}). Composition of {@link IDiscountRule}s.
 */
public class DiscountPolicy {

    private final List<IDiscountRule> rules = new ArrayList<>();

    public List<IDiscountRule> getRulesView() {
        return Collections.unmodifiableList(rules);
    }

    public void addRule(IDiscountRule rule) {
        rules.add(Objects.requireNonNull(rule));
    }
}
