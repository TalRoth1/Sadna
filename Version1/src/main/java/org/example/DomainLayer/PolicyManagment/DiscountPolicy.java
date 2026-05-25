package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DiscountPolicy {
    private final List<IDiscountRule> discounts = new ArrayList<>();

    public List<IDiscountRule> getDiscountRules() {
        return Collections.unmodifiableList(discounts);
    }

    public void addRule(IDiscountRule rule) {
        discounts.add(Objects.requireNonNull(rule));
    }

    public void removeRule(UUID id) {
        discounts.removeIf(rule -> Objects.equals(rule.getId(), id));
    }

    public float applyDiscount(ActivePurchase purchase) {
        return applyDiscount(purchase, null);
    }

    public float applyDiscount(ActivePurchase purchase, String couponCode) {
        if (purchase == null) {
            throw new IllegalArgumentException("Active purchase is required");
        }

        String normalizedCouponCode =
                couponCode == null || couponCode.isBlank()
                        ? null
                        : couponCode.trim();

        boolean couponWasProvided = normalizedCouponCode != null;
        boolean couponWasMatched = false;

        // Always start from the original ticket prices. Otherwise, after a
        // declined payment, a retry can apply the same discount on top of an
        // already-discounted ActivePurchase.price.
        float basePrice = purchase.getTicketIDs().values().stream().reduce(0.0f, Float::sum);
        purchase.setPrice(basePrice);
        float price = basePrice;

        for (IDiscountRule discountRule : discounts) {
            if (discountRule instanceof CouponCode couponRule) {
                if (!couponWasProvided) {
                    continue;
                }

                if (!couponRule.matchesCode(normalizedCouponCode)) {
                    continue;
                }

                couponWasMatched = true;
                price = couponRule.apply(purchase, normalizedCouponCode);
            } else {
                price = discountRule.apply(purchase);
            }

            purchase.setPrice(price);
        }

        if (couponWasProvided && !couponWasMatched) {
            throw new DomainException("Coupon code is not valid");
        }

        return price;
    }
}
