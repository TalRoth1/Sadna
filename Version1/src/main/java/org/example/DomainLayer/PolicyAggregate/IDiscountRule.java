package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;

/**
 * Discount rule; part of {@link DiscountPolicy}.
 */
public interface IDiscountRule {

    void apply(ActivePurchase activePurchase, Event event);
}
