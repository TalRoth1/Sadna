package org.example.DomainLayer.EventAggregate;

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

    public void validate()
    {
        //TODO
    }
}
