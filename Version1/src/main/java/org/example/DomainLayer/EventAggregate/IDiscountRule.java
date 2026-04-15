package org.example.DomainLayer.EventAggregate;

/**
 * Discount rule; part of {@link DiscountPolicy}.
 */
public interface IDiscountRule {

    void apply();
}
