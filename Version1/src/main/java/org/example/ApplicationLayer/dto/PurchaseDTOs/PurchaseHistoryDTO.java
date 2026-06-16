package org.example.ApplicationLayer.dto.PurchaseDTOs;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PurchaseHistoryDTO {
    public UUID userId;
    public UUID eventId;
    public List<UUID> ticketIds;

    // Denormalized event fields so the frontend can render and filter the
    // purchase-history page without firing N+1 queries to fetch each event.
    public String eventName;
    public LocalDateTime eventDate;
    public String eventLocation;
    public int ticketsAmount;
    // Note: field name matches the frontend JSON key (`totalPrice`).
    public double totalPrice;
    public String paymentInfo;     // Added to display the payment method (e.g. "Visa ****1234")

    // Secure confirmation/barcode returned by the external ticketing system.
    public String issuedTicketRef;

    // Human-readable seat descriptor per purchased ticket, aligned by index with
    // ticketIds (e.g. "Sitting area 1 · Row 2 · Seat 5" or "Standing area 1 ·
    // Standing"). Lets the purchase-history page show which seat each QR is for.
    public List<String> seatLabels;

    public LocalDateTime purchaseDate;

    public PurchaseHistoryDTO() {}
}
