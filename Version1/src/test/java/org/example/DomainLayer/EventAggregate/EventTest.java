package org.example.DomainLayer.EventAggregate;

import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class EventTest {

    private Event event;
    private final UUID companyId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @Before
    public void setUp() {
        // Initialize a real Event instance
        event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(7),
                "Be'er Sheva",
                "Nadav and the Coders",
                "Concert",
                EventStatus.ACTIVE
        );
    }

    @Test
    public void testAddPurchasePolicy_PersistsRulesInEventState() {
        // Arrange
        Optional<Float> age = Optional.of(16.0f);
        Optional<Integer> maxTickets = Optional.of(4);

        // Act
        event.addPurchasePolicy(age, Optional.empty(), maxTickets, Optional.of(true));

        // Assert
        var rules = event.getPurchasePolicy().getRulesView();
        assertEquals("Should have exactly 3 rules added", 3, rules.size());
        
        // Verify specific types
        assertTrue(rules.stream().anyMatch(r -> r instanceof AgeRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof MaxTicketRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof LoneSeatRule));
        assertFalse("MinTicketRule should not be present", rules.stream().anyMatch(r -> r instanceof MinTicketRule));
    }

    @Test
    public void testDeletePurchaseRule_RemovesCorrectRulesFromEvent() {
        // Arrange: Pre-populate rules
        event.addPurchasePolicy(Optional.of(18.0f), Optional.of(1), Optional.of(10), Optional.of(false));
        assertEquals(4, event.getPurchasePolicy().getRulesView().size());

        // Act: Delete Age and MaxTicket rules
        event.deletePurchaseRule(true, false, true, false);

        // Assert
        var rules = event.getPurchasePolicy().getRulesView();
        assertEquals("Should have 2 rules remaining", 2, rules.size());
        
        // Check remaining
        assertTrue(rules.stream().anyMatch(r -> r instanceof MinTicketRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof LoneSeatRule));
        
        // Check deleted
        assertFalse(rules.stream().anyMatch(r -> r instanceof AgeRule));
        assertFalse(rules.stream().anyMatch(r -> r instanceof MaxTicketRule));
    }

    @Test
    public void testAddPurchasePolicy_HandlesNullOptionalsGracefully() {
        // Act
        try {
            event.addPurchasePolicy(null, null, null, null);
        } catch (NullPointerException e) {
            fail("Event.addPurchasePolicy should handle null parameters with its internal null-checks");
        }

        assertTrue("Policy should remain empty if nulls are passed", 
                   event.getPurchasePolicy().getRulesView().isEmpty());
    }

    @Test
    public void testConstructor_ThrowsExceptionOnNullStatus() {
        // Testing the validation logic in your constructor
        assertThrows(IllegalArgumentException.class, () -> {
            new Event(eventId, companyId, LocalDateTime.now(), "Tel Aviv", "Artist", "Type", null);
        });
    }

    @Test
    public void testDeletePurchaseRule_IgnoresDeletionIfPolicyIsEmpty() {
        // Act
        event.deletePurchaseRule(true, true, true, true);

        // Assert
        assertTrue("No exception should be thrown when deleting from an empty policy", 
                   event.getPurchasePolicy().getRulesView().isEmpty());
    }
}