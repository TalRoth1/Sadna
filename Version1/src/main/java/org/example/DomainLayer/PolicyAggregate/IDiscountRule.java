package org.example.DomainLayer.PolicyAggregate;

/**
 * Discount rule; part of {@link DiscountPolicy}.
 */
public interface IDiscountRule {

    void apply();
}
