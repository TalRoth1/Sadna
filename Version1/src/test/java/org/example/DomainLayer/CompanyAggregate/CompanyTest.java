package org.example.DomainLayer.CompanyAggregate;

import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

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
}
