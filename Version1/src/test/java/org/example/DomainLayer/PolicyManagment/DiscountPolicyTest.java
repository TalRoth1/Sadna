package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DiscountPolicyTest {

    private DiscountPolicy discountPolicy;

    @Mock
    private IDiscountRule discount1;

    @Mock
    private IDiscountRule discount2;

    @Mock
    private ActivePurchase mockPurchase;

    @Before
    public void setUp() {
        discountPolicy = new DiscountPolicy();
    }

    @Test
    public void testApplyDiscount_NoDiscounts_ReturnsOriginalPrice() {
        // Arrange
        float originalPrice = 100.0f;
        when(mockPurchase.getPrice()).thenReturn(originalPrice);

        // Act
        float finalPrice = discountPolicy.applyDiscount(mockPurchase);

        // Assert
        assertEquals(originalPrice, finalPrice, 0.001);
        verify(mockPurchase, never()).setPrice(anyFloat());
    }

    @Test
    public void testApplyDiscount_MultipleDiscounts_AccumulatesCorrectly() {
        // Arrange
        float initialPrice = 200.0f;
        float priceAfterFirst = 180.0f; // 10% off
        float priceAfterSecond = 150.0f; // 30 off

        when(mockPurchase.getPrice()).thenReturn(initialPrice);
        
        // Mock the rules to transform the price sequentially
        when(discount1.apply(mockPurchase)).thenReturn(priceAfterFirst);
        when(discount2.apply(mockPurchase)).thenReturn(priceAfterSecond);

        discountPolicy.addRule(discount1);
        discountPolicy.addRule(discount2);

        // Act
        float finalPrice = discountPolicy.applyDiscount(mockPurchase);

        // Assert
        assertEquals(priceAfterSecond, finalPrice, 0.001);
        
        // Verify that the price was updated on the purchase object at each step
        verify(mockPurchase).setPrice(priceAfterFirst);
        verify(mockPurchase).setPrice(priceAfterSecond);
    }

    @Test
    public void testRemoveRule_SuccessfullyRemovesById() {
        // Arrange
        UUID idToRemove = UUID.randomUUID();
        UUID idToKeep = UUID.randomUUID();
        
        when(discount1.getId()).thenReturn(idToRemove);
        when(discount2.getId()).thenReturn(idToKeep);
        
        discountPolicy.addRule(discount1);
        discountPolicy.addRule(discount2);

        // Act
        discountPolicy.removeRule(idToRemove);

        // Assert
        assertEquals(1, discountPolicy.getDiscountRules().size());
        assertEquals(idToKeep, discountPolicy.getDiscountRules().get(0).getId());
    }

    @Test
    public void testAddRule_ThrowsExceptionOnNull() {
        assertThrows(NullPointerException.class, () -> {
            discountPolicy.addRule(null);
        });
    }
}