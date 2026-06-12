package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DiscountPolicyTest {

    private ActivePurchase mockPurchase;
    private Map<UUID, Float> sampleTickets;

    @BeforeEach
    void setUp() {
        mockPurchase = mock(ActivePurchase.class);
        sampleTickets = new HashMap<>();
        
        // Setup default behaviors for the purchase object mock
        when(mockPurchase.getTicketIDs()).thenReturn(sampleTickets);
        
        // Handle internal price mutations within ActivePurchase cleanly
        final float[] structuralPrice = {0.0f};
        doAnswer(invocation -> {
            structuralPrice[0] = invocation.getArgument(0);
            return null;
        }).when(mockPurchase).setPrice(anyFloat());
        
        when(mockPurchase.getPrice()).thenAnswer(invocation -> structuralPrice[0]);
    }

    @Nested
    @DisplayName("DiscountPolicy Core Infrastructure Tests")
    class CorePolicyTests {

        @Test
        @DisplayName("Should initialize properly with MAX type and manage internal rules")
        void testPolicyInitializationWithMaxType() {
            DiscountPolicy policy = new DiscountPolicy(DiscountType.MAX);

            assertTrue(policy.hasRules());
            assertNotNull(policy.getDiscountRules());
            assertEquals(0, policy.getDiscountRules().size());

            IDiscountRule mockRule = mock(IDiscountRule.class);
            when(mockRule.getId()).thenReturn(UUID.randomUUID());
            when(mockRule.getDiscountPercent()).thenReturn(10.0f);

            policy.addRule(mockRule);
            assertEquals(1, policy.getDiscountRules().size());

            policy.removeRule(mockRule.getId());
            assertEquals(0, policy.getDiscountRules().size());
        }

        @Test
        @DisplayName("Should initialize properly with ALL type and manage internal rules")
        void testPolicyInitializationWithAllType() {
            DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL);

            assertTrue(policy.hasRules());
            assertNotNull(policy.getDiscountRules());

            IDiscountRule mockRule = mock(IDiscountRule.class);
            when(mockRule.getId()).thenReturn(UUID.randomUUID());

            policy.addRule(mockRule);
            assertEquals(1, policy.getDiscountRules().size());
        }

        @Test
        @DisplayName("Should reset purchase price to tickets baseline sum during discount application")
        void testApplyDiscountResetsBasePrice() {
            DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL);
            
            // 3 tickets totaling 150.0f
            sampleTickets.put(UUID.randomUUID(), 50.0f);
            sampleTickets.put(UUID.randomUUID(), 40.0f);
            sampleTickets.put(UUID.randomUUID(), 60.0f);

            // Intentionally set a dirty pre-existing price to see if applyDiscount overrides it
            mockPurchase.setPrice(999.0f);

            float totalResult = policy.applyDiscount(mockPurchase);
            
            // With no rules added, result should exactly match the calculated baseline sum
            assertEquals(150.0f, totalResult, 0.01f);
            verify(mockPurchase, atLeastOnce()).setPrice(150.0f);
        }

        @Test
        @DisplayName("Should throw exception when applying discount on null purchase")
        void testApplyDiscountNullPurchase() {
            DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL);
            assertThrows(IllegalArgumentException.class, () -> policy.applyDiscount(null));
        }
    }

    @Nested
    @DisplayName("OvertDiscount Rule Tests")
    class OvertDiscountTests {

        @Test
        @DisplayName("Should apply percentage discount when date is within range")
        void testOvertDiscountActive() {
            LocalDate today = LocalDate.now();
            OvertDiscount discount = new OvertDiscount(20.0f, today.minusDays(2), today.plusDays(2));
            
            sampleTickets.put(UUID.randomUUID(), 100.0f);

            DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL);
            policy.addRule(discount);

            float result = policy.applyDiscount(mockPurchase);
            assertEquals(80.0f, result, 0.01f);
        }

        @Test
        @DisplayName("Should bypass discount calculation when out of date range")
        void testOvertDiscountExpired() {
            LocalDate today = LocalDate.now();
            OvertDiscount discount = new OvertDiscount(20.0f, today.minusDays(10), today.minusDays(2));
            
            sampleTickets.put(UUID.randomUUID(), 100.0f);

            DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL);
            policy.addRule(discount);

            float result = policy.applyDiscount(mockPurchase);
            assertEquals(100.0f, result, 0.01f);
        }
    }

    @Nested
    @DisplayName("ConditionalDiscount Rule Tests")
    class ConditionalDiscountTests {

        @Test
        @DisplayName("Should apply bundle discount to the lowest priced tickets via entire Policy")
        void testConditionalDiscountBundleMatching() {
            LocalDate today = LocalDate.now();
            // Buy 2 + Get 1 at 50% off. Total bundle = 3
            ConditionalDiscount conditionalDiscount = new ConditionalDiscount(
                    today.minusDays(1), today.plusDays(1), 50.0f, 2, 1
            );

            sampleTickets.put(UUID.randomUUID(), 100.0f);
            sampleTickets.put(UUID.randomUUID(), 80.0f);
            sampleTickets.put(UUID.randomUUID(), 60.0f); // 50% off 60.0f -> saves 30.0f

            DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL);
            policy.addRule(conditionalDiscount);

            float result = policy.applyDiscount(mockPurchase);
            assertEquals(210.0f, result, 0.01f); // 240 base - 30 saving
        }
    }

    @Nested
    @DisplayName("CouponCode Rule Tests")
    class CouponCodeTests {

        @Test
        @DisplayName("Should apply coupon discount if matching code is stored in purchase")
        void testCouponCodeAppliedSuccessfully() {
            LocalDate today = LocalDate.now();
            CouponCode coupon = new CouponCode(today.minusDays(1), today.plusDays(1), 10.0f, "SAVE10");

            sampleTickets.put(UUID.randomUUID(), 200.0f);
            when(mockPurchase.getCoupon()).thenReturn("SAVE10");

            DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL);
            policy.addRule(coupon);

            float result = policy.applyDiscount(mockPurchase);
            assertEquals(180.0f, result, 0.01f);
        }

        @Test
        @DisplayName("Should throw DomainException if coupon code matches but dates are expired")
        void testCouponExpiredThrowsException() {
            LocalDate today = LocalDate.now();
            CouponCode coupon = new CouponCode(today.minusDays(10), today.minusDays(2), 10.0f, "EXPIRED10");

            sampleTickets.put(UUID.randomUUID(), 200.0f);
            when(mockPurchase.getCoupon()).thenReturn("EXPIRED10");

            DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL);
            policy.addRule(coupon);

            assertThrows(DomainException.class, () -> policy.applyDiscount(mockPurchase));
        }
    }

    @Nested
    @DisplayName("Composite Calculation Strategy Tests")
    class CompositeCalculationTests {

        @Test
        @DisplayName("DiscountType.ALL: Should chain multiple discounts sequentially")
        void testAllDiscountChainingStrategy() {
            DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL);
            LocalDate today = LocalDate.now();

            OvertDiscount rule1 = new OvertDiscount(10.0f, today.minusDays(1), today.plusDays(1)); // 10% off
            OvertDiscount rule2 = new OvertDiscount(20.0f, today.minusDays(1), today.plusDays(1)); // 20% off

            policy.addRule(rule1);
            policy.addRule(rule2);

            sampleTickets.put(UUID.randomUUID(), 100.0f);

            // Calculation pipeline: 100.0 base -> rule1 (90.0) -> rule2 (72.0)
            float result = policy.applyDiscount(mockPurchase);
            assertEquals(72.0f, result, 0.01f);
        }

        @Test
        @DisplayName("DiscountType.MAX: Should choose only the rule yielding the highest discount rate")
        void testMaxDiscountSelectionStrategy() {
            DiscountPolicy policy = new DiscountPolicy(DiscountType.MAX);
            LocalDate today = LocalDate.now();

            OvertDiscount ruleLow = new OvertDiscount(10.0f, today.minusDays(1), today.plusDays(1));
            OvertDiscount ruleHigh = new OvertDiscount(50.0f, today.minusDays(1), today.plusDays(1));
            OvertDiscount ruleMid = new OvertDiscount(25.0f, today.minusDays(1), today.plusDays(1));

            // Adding out of order to verify manual sorted insertion loop in MaxDiscount
            policy.addRule(ruleLow);
            policy.addRule(ruleHigh);
            policy.addRule(ruleMid);

            sampleTickets.put(UUID.randomUUID(), 100.0f);

            // Must pick ruleHigh exclusively (50% off 100 = 50)
            float result = policy.applyDiscount(mockPurchase);
            assertEquals(50.0f, result, 0.01f);
        }
    }
}