package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Discount rules for an event (1:1 with {@link Event}). Composition of {@link IDiscountRule}s.
 */
public class DiscountPolicy {

    private final FreeTicketsRule freeTicketsRule;
    private final List<VisibleSaleRule> visibleSaleRules;
    private final List<CouponRule> couponRules;

    public DiscountPolicy(FreeTicketsRule freeTicketsRule, List<VisibleSaleRule> visibleSaleRules, List<CouponRule> couponRules) {
        this.freeTicketsRule = freeTicketsRule;
        this.visibleSaleRules = visibleSaleRules;
        this.couponRules = couponRules;
    }

    public void apply(ActivePurchase ap, Event event)
    {
        if (freeTicketsRule != null) {
            freeTicketsRule.apply(ap, event);
        }

        for (VisibleSaleRule rule : visibleSaleRules) {
            rule.apply(ap, event);
        }
        for (CouponRule rule : couponRules) {
            rule.apply(ap, event);
        }
    }
}
