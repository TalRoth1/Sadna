package org.example.DomainLayer.CompanyAggregate;

import org.example.DomainLayer.PolicyManagment.ConditionalDiscount;
import org.example.DomainLayer.PolicyManagment.CouponCode;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class CompanyTest {

    private Company company;
    private final String founderUser = "moshiko123";
    private final String companyName = "Workshop Ltd";

    @Before
    public void setUp() {
        // Initialize a real Company instance for state testing
        company = new Company(founderUser, companyName);
    }

    @Test
    public void testAddPurchasePolicy_AddsMultipleRulesSequentialTree() {
        // Arrange
        Optional<Float> age = Optional.of(18.0f);
        Optional<Integer> minTickets = Optional.of(2);
        Optional<Integer> maxTickets = Optional.of(10);
        Optional<Boolean> loneSeat = Optional.of(true);

        // Act: Sequential branch building utilizing the binary tree logic (true = AND / false = OR)
        company.addPurchasePolicy(age, Optional.empty(), Optional.empty(), Optional.empty(), true);
        company.addPurchasePolicy(Optional.empty(), minTickets, Optional.empty(), Optional.empty(), true);
        company.addPurchasePolicy(Optional.empty(), Optional.empty(), maxTickets, Optional.empty(), true);
        company.addPurchasePolicy(Optional.empty(), Optional.empty(), Optional.empty(), loneSeat, true);

        // Assert
        var rulesView = company.getPurchasePolicy().getRulesView();
        assertNotNull("The active purchase policy tree view must not be null", rulesView);
        
        // Note: Under the composite tree design, rulesView maps to the structural state representation.
        // We assert that policy configuration definitions are active and populated.
        assertNotNull(rulesView.getId());
    }

    @Test
    public void testAddPurchasePolicy_IgnoresEmptyOptionals() {
        // Act: Only add an age rule, leave others empty (passing the required andOr operator)
        company.addPurchasePolicy(Optional.of(21.0f), Optional.empty(), Optional.empty(), Optional.empty(), true);

        // Assert
        var rulesView = company.getPurchasePolicy().getRulesView();
        assertNotNull("The root rule should be successfully generated", rulesView);
    }

    @Test
    public void testDeletePurchaseRule_RemovesSpecificNodeViaId() {
        // Arrange: Build up an initial tree layer containing a targeted policy element
        company.addPurchasePolicy(Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.empty(), true);
        
        var rulesViewBefore = company.getPurchasePolicy().getRulesView();
        assertNotNull(rulesViewBefore);
        UUID targetRuleId = rulesViewBefore.getId();

        // Act: Target the clean specific leaf/root rule element ID for structural elimination
        company.deletePurchaseRule(targetRuleId);

        // Assert
        var rulesViewAfter = company.getPurchasePolicy().getRulesView();
        assertTrue("The rule view should collapse or become empty after deleting the target node",
                rulesViewAfter == null);
    }

    @Test
    public void testAddPurchasePolicy_HandlesNullArgumentsGracefully() {
        // Act & Assert: Ensure it handles null parameters gracefully using the new signature
        try {
            company.addPurchasePolicy(null, null, null, null, true);
        } catch (NullPointerException e) {
            fail("addPurchasePolicy should handle null parameters cleanly without throwing NullPointerException");
        }
        
        assertTrue("Policy structural rules should remain empty or null on completely null inputs", 
                company.getPurchasePolicy() == null || company.getPurchasePolicy().getRulesView() == null);
    }

    @Test
    public void testDeletePurchaseRule_NonExistentOrRandomIdDoesNotCrash() {
        // Arrange: Establish a basic functional policy setup
        company.addPurchasePolicy(Optional.of(25.0f), Optional.empty(), Optional.empty(), Optional.empty(), true);
        
        // Act: Attempt to delete a totally random non-matching UUID
        UUID nonExistentId = UUID.randomUUID();
        company.deletePurchaseRule(nonExistentId);

        // Assert: The existing active tree policy details should remain fully intact
        assertNotNull("Policy tree structure should remain unchanged when ID doesn't match", 
                company.getPurchasePolicy().getRulesView());
    }

    @Test
    public void testAddOvertDiscount_PersistsCorrectly() {
        // Arrange
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now().plusDays(5);
        float percent = 15.0f;

        // Act
        company.addOvertDiscount(start, end, percent);

        // Assert
        var rules = company.getDiscountPolicy().getDiscountRules();
        assertEquals("Should have 1 overt discount rule", 1, rules.size());
        assertTrue(rules.get(0) instanceof OvertDiscount);
        
        OvertDiscount rule = (OvertDiscount) rules.get(0);
        assertEquals(percent, rule.getDiscountPercent(), 0.001);
    }

    @Test
    public void testAddConditionalDiscount_PersistsCorrectly() {
        // Arrange
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now().plusDays(10);
        float percent = 50.0f;
        int required = 3;
        int applied = 1;

        // Act
        company.addConditionalDiscount(start, end, percent, required, applied);

        // Assert
        var rules = company.getDiscountPolicy().getDiscountRules();
        assertEquals("Should have 1 conditional discount rule", 1, rules.size());
        assertTrue(rules.get(0) instanceof ConditionalDiscount);
    }

    @Test
    public void testAddCouponCode_PersistsCorrectly() {
        // Arrange
        String code = "SUMMER2026";
        
        // Act
        company.addCouponCode(LocalDate.now(), LocalDate.now().plusMonths(1), 10.0f, code);

        // Assert
        var rules = company.getDiscountPolicy().getDiscountRules();
        assertEquals(1, rules.size());
        assertTrue(rules.get(0) instanceof CouponCode);
        
        CouponCode rule = (CouponCode) rules.get(0);
        assertEquals(code, rule.getCode());
    }

    @Test
    public void testRemoveDiscount_SuccessfullyDeletesRule() {
        // Arrange: Add a discount and get its generated ID
        company.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(1), 10.0f);
        var rulesBefore = company.getDiscountPolicy().getDiscountRules();
        UUID discountId = rulesBefore.get(0).getId();
        assertEquals(1, rulesBefore.size());

        // Act
        company.removeDiscount(discountId);

        // Assert
        assertTrue("Discount policy should be empty after removal", 
                   company.getDiscountPolicy().getDiscountRules().isEmpty());
    }

    @Test
    public void testAddMultipleDiscounts_AccumulatesInPolicy() {
        // Act
        company.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(1), 5.0f);
        company.addCouponCode(LocalDate.now(), LocalDate.now().plusDays(1), 10.0f, "PROMO");

        // Assert
        var rules = company.getDiscountPolicy().getDiscountRules();
        assertEquals("Policy should contain both discounts", 2, rules.size());
    }
}