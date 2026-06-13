package org.example.InfrastructureLayer.Persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_history")
public class PurchaseHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "purchase_info", nullable = false, columnDefinition = "jsonb")
    private String purchaseInfo;

    @Column(name = "purchase_total", nullable = false)
    private double purchaseTotal;

    @Column(name = "purchase_date", nullable = false)
    private LocalDateTime purchaseDate;

    // Confirmation/secure code returned by the external ticketing system.
    // Immutable: set once at construction, exposed read-only.
    @Column(name = "issued_ticket_ref")
    private String issuedTicketRef;

    // External clearing-system transaction id captured at charge time, used
    // to refund the buyer if the event is later cancelled. Immutable.
    @Column(name = "payment_transaction_id")
    private Integer paymentTransactionId;

    protected PurchaseHistoryEntity() {
    }

    public PurchaseHistoryEntity(UUID id,
                                 UUID userId,
                                 UUID eventId,
                                 String purchaseInfo,
                                 double purchaseTotal,
                                 LocalDateTime purchaseDate,
                                 String issuedTicketRef,
                                 Integer paymentTransactionId) {
        this.id = id;
        this.userId = userId;
        this.eventId = eventId;
        this.purchaseInfo = purchaseInfo;
        this.purchaseTotal = purchaseTotal;
        this.purchaseDate = purchaseDate;
        this.issuedTicketRef = issuedTicketRef;
        this.paymentTransactionId = paymentTransactionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getPurchaseInfo() {
        return purchaseInfo;
    }

    public double getPurchaseTotal() {
        return purchaseTotal;
    }

    public LocalDateTime getPurchaseDate() {
        return purchaseDate;
    }

    public String getIssuedTicketRef() {
        return issuedTicketRef;
    }

    public Integer getPaymentTransactionId() {
        return paymentTransactionId;
    }
}

