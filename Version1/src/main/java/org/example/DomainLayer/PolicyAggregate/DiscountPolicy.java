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
    private final CouponRule couponRule;

    public DiscountPolicy(FreeTicketsRule freeTicketsRule, List<VisibleSaleRule> visibleSaleRules, CouponRule couponRule) {
        this.freeTicketsRule = freeTicketsRule;
        this.visibleSaleRules = visibleSaleRules;
        this.couponRule = couponRule;
    }

    public void apply(ActivePurchase ap, Event event, String couponCode)
    {
        if (freeTicketsRule != null) {
            freeTicketsRule.apply(ap, event);
        }

        for (VisibleSaleRule rule : visibleSaleRules) {
            rule.apply(ap, event);
        }
        couponRule.setEnteredCode(couponCode);
        couponRule.apply(ap, event);
    }
}
