package org.example.InfrastructureLayer.Persistence;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.InfrastructureLayer.Persistence.SpringDataHistoryRepository;
import org.example.InfrastructureLayer.Persistence.SpringDataTicketRepository;
import org.example.InfrastructureLayer.Persistence.PurchaseHistoryEntity;
import org.example.InfrastructureLayer.Persistence.TicketEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@Profile("localdb")
public class JpaHistoryRepository implements IHistoryRepository {

    private static final ObjectMapper PAYMENT_INFO_MAPPER = new ObjectMapper();

    private final SpringDataHistoryRepository historyJpa;
    private final SpringDataTicketRepository ticketJpa;

    public JpaHistoryRepository(SpringDataHistoryRepository historyJpa,
                                SpringDataTicketRepository ticketJpa) {
        this.historyJpa = historyJpa;
        this.ticketJpa = ticketJpa;
    }

    @Override
    public void add(PurchaseHistory purchaseHistory) {
        if (purchaseHistory == null) {
            throw new IllegalArgumentException("PurchaseHistory is required");
        }

        UUID historyId = UUID.randomUUID();

        String rawPaymentInfo = purchaseHistory.getPayment().getPaymentInfo();

        if (rawPaymentInfo == null || rawPaymentInfo.isBlank()) {
            rawPaymentInfo = "Valid payment";
        }

        String paymentInfoJson = "{\"info\":\"" +
                rawPaymentInfo
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"") +
                "\"}";

        PurchaseHistoryEntity entity = new PurchaseHistoryEntity(
                historyId,
                purchaseHistory.getUserId(),
                purchaseHistory.getEventId(),
                paymentInfoJson,
                purchaseHistory.getPayment().getTotal(),
                purchaseHistory.getPurchaseDate(),
                purchaseHistory.getIssuedTicketReference(),
                purchaseHistory.getPayment().getTransactionId()
        );

        historyJpa.save(entity);

        // associate tickets with this purchase history id
        if (purchaseHistory.getTicketIds() != null && !purchaseHistory.getTicketIds().isEmpty()) {
            var tickets = ticketJpa.findAllById(purchaseHistory.getTicketIds());
            for (TicketEntity t : tickets) {
                t.setPurchaseHistoryId(historyId);
                t.setActivePurchaseId(null);
            }
            ticketJpa.saveAll(tickets);
        }
    }

    @Override
    public List<PurchaseHistory> getAll() {
        return historyJpa.findAll()
            .stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<PurchaseHistory> getByUserId(UUID userId) {
        if (userId == null) {
            return List.of();
        }

        return historyJpa.findByUserId(userId)
            .stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<PurchaseHistory> getByEventId(UUID eventId) {
        if (eventId == null) {
            return List.of();
        }

        return historyJpa.findByEventId(eventId)
            .stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    private PurchaseHistory toDomain(PurchaseHistoryEntity entity) {
        int transactionId = entity.getPaymentTransactionId() == null
                ? -1
                : entity.getPaymentTransactionId();
        Payment payment = new Payment(
                entity.getPurchaseTotal(), parsePaymentInfo(entity.getPurchaseInfo()), transactionId);

        List<UUID> ticketIds = ticketJpa.findByPurchaseHistoryId(entity.getId())
            .stream()
            .map(TicketEntity::getId)
            .collect(Collectors.toList());

        return new PurchaseHistory(
            entity.getUserId(),
            ticketIds,
            entity.getEventId(),
            payment,
            entity.getPurchaseDate(),
            entity.getIssuedTicketRef()
        );
    }

    /**
     * The payment info is persisted as a small JSON envelope ({@code {"info":"..."}}).
     * Unwrap it back to the human-readable value so the UI shows e.g.
     * "Valid payment" rather than the raw JSON. Legacy/plain values (not JSON)
     * are returned as-is.
     */
    private String parsePaymentInfo(String stored) {
        if (stored == null || stored.isBlank()) {
            return "Valid payment";
        }

        String trimmed = stored.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode info = PAYMENT_INFO_MAPPER.readTree(trimmed).get("info");
                if (info != null && !info.isNull()) {
                    return info.asText();
                }
            } catch (Exception ignored) {
                // Not valid JSON — fall back to the raw stored value below.
            }
        }

        return stored;
    }
}

