package org.example.ApplicationLayer.dto.PurchaseDTOs;

import java.util.List;

/**
 * Checkout response payload. Carries the external ticketing system's issuance
 * confirmation codes back to the client so it can show a digital ticket /
 * barcode per purchased ticket.
 */
public class CompletePurchaseResponse {
    public String issuedTicketRef;
    public List<String> issuedTicketRefs;

    public CompletePurchaseResponse() {
    }

    public CompletePurchaseResponse(String issuedTicketRef) {
        this.issuedTicketRef = issuedTicketRef;
        this.issuedTicketRefs =
                issuedTicketRef == null || issuedTicketRef.isBlank()
                        ? List.of()
                        : List.of(issuedTicketRef);
    }

    public CompletePurchaseResponse(List<String> issuedTicketRefs) {
        this.issuedTicketRefs = issuedTicketRefs == null ? List.of() : issuedTicketRefs;
        this.issuedTicketRef = this.issuedTicketRefs.isEmpty() ? null : this.issuedTicketRefs.get(0);
    }
}