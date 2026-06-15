package org.example.ApplicationLayer.dto.PurchaseDTOs;

/**
 * Checkout response payload. Carries the external ticketing system's issuance
 * confirmation (the secure ticket code) back to the client so it can be shown
 * immediately on the confirmation screen as a digital ticket / barcode.
 */
public class CompletePurchaseResponse {
    public String issuedTicketRef;

    public CompletePurchaseResponse() {
    }

    public CompletePurchaseResponse(String issuedTicketRef) {
        this.issuedTicketRef = issuedTicketRef;
    }
}
