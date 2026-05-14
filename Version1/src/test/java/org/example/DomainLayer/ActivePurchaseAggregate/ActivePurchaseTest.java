package org.example.DomainLayer.ActivePurchaseAggregate;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class ActivePurchaseTest {

    @Test
    public void constructor_whenCreated_setsFieldsAndCalculatesTotalPrice() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        UUID ticket1 = UUID.randomUUID();
        UUID ticket2 = UUID.randomUUID();

        Map<UUID, Float> ticketPrices = new LinkedHashMap<>();
        ticketPrices.put(ticket1, 100f);
        ticketPrices.put(ticket2, 50f);

        LocalDateTime endTime = LocalDateTime.now().plusMinutes(10);

        ActivePurchase purchase =
                new ActivePurchase(userId, eventId, ticketPrices, endTime);

        assertNotNull(purchase.getActivePurchaseId());
        assertEquals(userId, purchase.getUserID());
        assertEquals(eventId, purchase.getEventID());
        assertEquals(endTime, purchase.getEndTime());

        assertEquals(150f, purchase.getPrice(), 0.001);

        assertEquals(2, purchase.getTicketIDs().size());
        assertTrue(purchase.getTicketIDs().containsKey(ticket1));
        assertTrue(purchase.getTicketIDs().containsKey(ticket2));
    }

    @Test
    public void getTicketIDs_whenCalled_returnsUnmodifiableCopy() {
        UUID ticketId = UUID.randomUUID();

        Map<UUID, Float> ticketPrices = new LinkedHashMap<>();
        ticketPrices.put(ticketId, 100f);

        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ticketPrices,
                        LocalDateTime.now().plusMinutes(10)
                );

        Map<UUID, Float> returnedMap = purchase.getTicketIDs();

        assertThrows(UnsupportedOperationException.class, () ->
                returnedMap.put(UUID.randomUUID(), 200f)
        );
    }

    @Test
    public void isExpired_whenCurrentTimeIsBeforeEndTime_returnsFalse() {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(10);

        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Map.of(UUID.randomUUID(), 100f),
                        endTime
                );

        assertFalse(purchase.isExpired(LocalDateTime.now()));
    }

    @Test
    public void isExpired_whenCurrentTimeEqualsEndTime_returnsTrue() {
        LocalDateTime endTime = LocalDateTime.now();

        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Map.of(UUID.randomUUID(), 100f),
                        endTime
                );

        assertTrue(purchase.isExpired(endTime));
    }

    @Test
    public void isExpired_whenCurrentTimeIsAfterEndTime_returnsTrue() {
        LocalDateTime endTime = LocalDateTime.now().minusMinutes(1);

        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Map.of(UUID.randomUUID(), 100f),
                        endTime
                );

        assertTrue(purchase.isExpired(LocalDateTime.now()));
    }

    @Test
    public void setPrice_whenCalled_updatesPrice() {
        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Map.of(UUID.randomUUID(), 100f),
                        LocalDateTime.now().plusMinutes(10)
                );

        purchase.setPrice(75f);

        assertEquals(75f, purchase.getPrice(), 0.001);
    }

    @Test
    public void setCoupon_whenCalled_updatesCouponCode() {
        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Map.of(UUID.randomUUID(), 100f),
                        LocalDateTime.now().plusMinutes(10)
                );

        purchase.setCoupon("SUMMER25");

        assertEquals("SUMMER25", purchase.getCoupon());
    }

    @Test
    public void setGuestAgeConfirmed_whenCalled_updatesGuestAgeConfirmation() {
        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Map.of(UUID.randomUUID(), 100f),
                        LocalDateTime.now().plusMinutes(10)
                );

        purchase.SetGuestAgeConfirmed(true);

        assertTrue(purchase.getGuestAgeConfirmed());
    }

    @Test
    public void setMaxWaitTime_whenCalled_updatesMaxWaitTime() {
        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Map.of(UUID.randomUUID(), 100f),
                        LocalDateTime.now().plusMinutes(10)
                );

        purchase.setMaxWaitTime(5f);

        assertEquals(5f, purchase.getMaxWaitTime(), 0.001);
    }

    @Test
    public void update_whenCalled_changesLastUpdateTime() throws InterruptedException {
        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Map.of(UUID.randomUUID(), 100f),
                        LocalDateTime.now().plusMinutes(10)
                );

        LocalDateTime beforeUpdate = purchase.getLastUpdate();

        Thread.sleep(2);

        purchase.update();

        assertTrue(purchase.getLastUpdate().isAfter(beforeUpdate));
    }

    @Test
    public void setNewTicketPricePrice_whenTicketExists_updatesTicketPrice() {
        UUID ticketId = UUID.randomUUID();

        Map<UUID, Float> ticketPrices = new LinkedHashMap<>();
        ticketPrices.put(ticketId, 100f);

        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ticketPrices,
                        LocalDateTime.now().plusMinutes(10)
                );

        purchase.setNewTicketPricePrice(ticketId, 70f);

        assertEquals(70f, purchase.getCurrentPrice(ticketId), 0.001);
    }

    @Test
    public void replaceTickets_whenCalled_replacesExistingTickets() {
        UUID oldTicket = UUID.randomUUID();
        UUID newTicket = UUID.randomUUID();

        Map<UUID, Float> oldTickets = new LinkedHashMap<>();
        oldTickets.put(oldTicket, 100f);

        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        oldTickets,
                        LocalDateTime.now().plusMinutes(10)
                );

        LinkedHashMap<UUID, Float> newTickets = new LinkedHashMap<>();
        newTickets.put(newTicket, 200f);

        purchase.replaceTickets(newTickets);

        assertFalse(purchase.getTicketIDs().containsKey(oldTicket));
        assertTrue(purchase.getTicketIDs().containsKey(newTicket));

        assertEquals(200f, purchase.getCurrentPrice(newTicket), 0.001);
    }

    @Test
    public void getCurrentPrice_whenTicketExists_returnsTicketPrice() {
        UUID ticketId = UUID.randomUUID();

        Map<UUID, Float> ticketPrices = new LinkedHashMap<>();
        ticketPrices.put(ticketId, 120f);

        ActivePurchase purchase =
                new ActivePurchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ticketPrices,
                        LocalDateTime.now().plusMinutes(10)
                );

        assertEquals(120f, purchase.getCurrentPrice(ticketId), 0.001);
    }
}