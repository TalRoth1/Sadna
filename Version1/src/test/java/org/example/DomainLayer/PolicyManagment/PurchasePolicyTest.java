package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PurchasePolicyTest {

    private PurchasePolicy purchasePolicy;

    // Create unique classes so .getClass() doesn't cause them to overwrite each other
    private static abstract class RuleTypeA implements IPurchaseRule {}
    private static abstract class RuleTypeB implements IPurchaseRule {}

    @Mock private RuleTypeA rule1;
    @Mock private RuleTypeB rule2;
    @Mock private ActivePurchase mockPurchase;
    @Mock private User mockUser;
    @Mock private Event mockEvent;

    @Before
    public void setUp() {
        purchasePolicy = new PurchasePolicy();
    }

    @Test
    public void testValidate_NoRules_ReturnsTrue() {
        // A policy with no rules should always pass validation
        boolean result = purchasePolicy.validate(mockPurchase, mockUser, mockEvent);
        assertTrue("Validation should pass if there are no rules", result);
    }

    @Test
    public void testValidate_AllRulesPass_ReturnsTrue() {
        // Arrange
        when(rule1.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(true);
        when(rule2.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(true);
        
        purchasePolicy.addRule(rule1);
        purchasePolicy.addRule(rule2); // rule1 is NOT removed now because RuleTypeA != RuleTypeB

        // Act
        boolean result = purchasePolicy.validate(mockPurchase, mockUser, mockEvent);

        // Assert
        assertTrue(result);
        verify(rule1).doesHold(mockPurchase, mockUser, mockEvent); // This will now pass!
        verify(rule2).doesHold(mockPurchase, mockUser, mockEvent);
    }

    @Test
    public void testValidate_OneRuleFails_ReturnsFalse() {
        // Arrange
        when(rule1.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(true);
        when(rule2.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(false);
        
        purchasePolicy.addRule(rule1);
        purchasePolicy.addRule(rule2);

        // Act
        boolean result = purchasePolicy.validate(mockPurchase, mockUser, mockEvent);

        // Assert
        assertFalse("Validation should fail if at least one rule does not hold", result);
    }

    @Test
    public void testValidate_ShortCircuitLogic() {
        // Arrange
        when(rule1.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(false);
        
        purchasePolicy.addRule(rule1);
        purchasePolicy.addRule(rule2);

        // Act
        purchasePolicy.validate(mockPurchase, mockUser, mockEvent);

        // Assert
        // rule1 failed, so rule2 should never even be checked (efficiency)
        verify(rule1, times(1)).doesHold(mockPurchase, mockUser, mockEvent);
        verify(rule2, never()).doesHold(any(), any(), any());
    }

    @Test
    public void testAddRule_ReplacesRuleOfSameClass() {
        // This tests your logic: rules.removeIf(existingRule -> existingRule.getClass().equals(rule.getClass()));
        // We use real rule instances or specifically typed mocks to test the Class equality logic
        AgeRule firstAgeRule = new AgeRule(18.0f);
        AgeRule secondAgeRule = new AgeRule(21.0f);

        purchasePolicy.addRule(firstAgeRule);
        purchasePolicy.addRule(secondAgeRule);

        assertEquals("Should only have 1 rule because the second AgeRule replaced the first", 
                     1, purchasePolicy.getRulesView().size());
        
        AgeRule remainingRule = (AgeRule) purchasePolicy.getRulesView().get(0);
        assertEquals(21.0f, remainingRule.getMinAge(), 0.01);
    }
}