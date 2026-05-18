package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PurchasePolicyTest {

    private PurchasePolicy purchasePolicy;

    // Custom abstract classes so .getClass() represents distinct entities within the tree
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
        // A policy with an uninitialized or empty tree node passes validation
        boolean result = purchasePolicy.validate(mockPurchase, mockUser, mockEvent);
        assertTrue("Validation should pass if there are no rules configured", result);
    }

    @Test
    public void testValidate_AllCompositeRulesPass_ReturnsTrue() {
        // Arrange
        when(rule1.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(true);
        when(rule2.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(true);
        
        // Form a binary composition tree (AND logical connection)
        purchasePolicy.addRule(rule1, true);
        purchasePolicy.addRule(rule2, true);

        // Act
        boolean result = purchasePolicy.validate(mockPurchase, mockUser, mockEvent);

        // Assert
        assertTrue("Validation should pass when both composite tree branches evaluate to true", result);
        verify(rule1).doesHold(mockPurchase, mockUser, mockEvent);
        verify(rule2).doesHold(mockPurchase, mockUser, mockEvent);
    }

    @Test
    public void testValidate_OneRuleFailsInAndComposition_ReturnsFalse() {
        // Arrange
        when(rule1.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(true);
        when(rule2.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(false);
        
        // Combine rule conditions using AND logic
        purchasePolicy.addRule(rule1, true);
        purchasePolicy.addRule(rule2, true);

        // Act
        boolean result = purchasePolicy.validate(mockPurchase, mockUser, mockEvent);

        // Assert
        assertFalse("Validation should fail if a nested condition in an AND block does not hold", result);
    }

    @Test
    public void testValidate_ShortCircuitLogicInAndComposition() {
        // Arrange
        // First rule evaluated in the sequential composition evaluates to false
        when(rule1.doesHold(mockPurchase, mockUser, mockEvent)).thenReturn(false);
        
        purchasePolicy.addRule(rule1, true);
        purchasePolicy.addRule(rule2, true);

        // Act
        purchasePolicy.validate(mockPurchase, mockUser, mockEvent);

        // Assert
        // Due to logical short-circuiting in an AND block, if rule1 breaks, rule2 shouldn't be executed
        verify(rule1, times(1)).doesHold(mockPurchase, mockUser, mockEvent);
        verify(rule2, never()).doesHold(any(), any(), any());
    }

    @Test
    public void testRemoveRule_RemovesStructuralNodeSuccessfully() {
        // Arrange: Seed policy state with an active rule element
        UUID ruleId = UUID.randomUUID();
        when(rule1.getId()).thenReturn(ruleId);
        purchasePolicy.addRule(rule1, true);
        
        assertNotNull("Rules configuration view must be present after addition", purchasePolicy.getRulesView());
        
        // Act: Target specific node structural identity for tree reduction removal
        purchasePolicy.removeRule(ruleId);

        // Assert
        assertNull("The rules composition view tree should clear out after removing the target node component", 
                purchasePolicy.getRulesView());
    }

    @Test
    public void testRemoveRule_HandlesNonExistentIdGracefully() {
        // Arrange
        UUID ruleId = UUID.randomUUID();
        when(rule1.getId()).thenReturn(ruleId);
        purchasePolicy.addRule(rule1, true);
        
        // Act: Attempt to remove a completely unmapped ID from the active tree structure
        purchasePolicy.removeRule(UUID.randomUUID());

        // Assert
        assertNotNull("The active rules tree view node must remain intact if the target identifier is not matched", 
                purchasePolicy.getRulesView());
    }
}