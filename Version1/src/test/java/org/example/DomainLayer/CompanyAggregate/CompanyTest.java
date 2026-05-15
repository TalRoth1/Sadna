package org.example.DomainLayer.CompanyAggregate;

import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.ConditionalDiscount;
import org.example.DomainLayer.PolicyManagment.CouponCode;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
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
    public void testAddPurchasePolicy_AddsMultipleRulesCorrectly() {
        // Arrange
        Optional<Float> age = Optional.of(18.0f);
        Optional<Integer> minTickets = Optional.of(2);
        Optional<Integer> maxTickets = Optional.of(10);
        Optional<Boolean> loneSeat = Optional.of(true);

        // Act
        company.addPurchasePolicy(age, minTickets, maxTickets, loneSeat);

        // Assert
        var rules = company.getPurchasePolicy().getRulesView();
        assertEquals("Should have exactly 4 rules added", 4, rules.size());
        
        // Verify types are present
        assertTrue(rules.stream().anyMatch(r -> r instanceof AgeRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof MinTicketRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof MaxTicketRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof LoneSeatRule));
    }

    @Test
    public void testAddPurchasePolicy_IgnoresEmptyOptionals() {
        // Act: Only add an age rule, leave others empty
        company.addPurchasePolicy(Optional.of(21.0f), Optional.empty(), Optional.empty(), Optional.empty());

        // Assert
        var rules = company.getPurchasePolicy().getRulesView();
        assertEquals("Should only have 1 rule", 1, rules.size());
        assertTrue("Rule should be an AgeRule", rules.get(0) instanceof AgeRule);
    }

    @Test
    public void testDeletePurchaseRule_RemovesSpecificRules() {
        // Arrange: Add rules first
        company.addPurchasePolicy(Optional.of(18.0f), Optional.of(1), Optional.empty(), Optional.of(false));
        assertEquals(3, company.getPurchasePolicy().getRulesView().size());

        // Act: Delete only age and allowLoneSeat
        company.deletePurchaseRule(true, false, false, true);

        // Assert
        var rules = company.getPurchasePolicy().getRulesView();
        assertEquals("Should have 1 rule remaining", 1, rules.size());
        assertTrue("The remaining rule should be MinTicketRule", rules.get(0) instanceof MinTicketRule);
        assertFalse("AgeRule should be gone", rules.stream().anyMatch(r -> r instanceof AgeRule));
    }

    @Test
    public void testAddPurchasePolicy_HandlesNullArguments() {
        // Act & Assert: Ensure it doesn't throw NullPointerException if null is passed instead of Optional
        try {
            company.addPurchasePolicy(null, null, null, null);
        } catch (NullPointerException e) {
            fail("addPurchasePolicy should handle null parameters gracefully via the null checks provided in the implementation");
        }
        
        assertTrue("Policy should remain empty", company.getPurchasePolicy().getRulesView().isEmpty());
    }

    @Test
    public void testDeletePurchaseRule_NoRulesToDelete() {
        // Act: Try to delete from an empty policy
        company.deletePurchaseRule(true, true, true, true);

        // Assert
        assertTrue("Policy should still be empty without throwing errors", 
                   company.getPurchasePolicy().getRulesView().isEmpty());
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
        UUID discountId = rulesBefore.get(0).getId(); // Assuming your Discount rules have getId()
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
