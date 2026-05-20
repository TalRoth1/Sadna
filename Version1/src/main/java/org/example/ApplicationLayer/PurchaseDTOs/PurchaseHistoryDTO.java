package org.example.ApplicationLayer.PurchaseDTOs;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PurchaseHistoryDTO {
    public UUID userId;
    public UUID eventId;
    public List<UUID> ticketIds;

    // השטחה של אובייקט ה-Payment:
    public double totalPaid;       // הותאם לטיפוס double של המחלקה
    public String paymentInfo;     // הוסף כדי להציג באיזו דרך שולם (למשל "Visa ****1234")

    public LocalDateTime purchaseDate;

    public PurchaseHistoryDTO() {}
}