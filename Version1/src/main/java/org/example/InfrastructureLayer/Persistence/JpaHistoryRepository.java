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
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("localdb")
@Transactional
public class JpaHistoryRepository implements IHistoryRepository {

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
                purchaseHistory.getPurchaseDate()
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
    @Transactional(readOnly = true)
    public List<PurchaseHistory> getAll() {
        return historyJpa.findAll()
            .stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
        Payment payment = new Payment(entity.getPurchaseTotal(), entity.getPurchaseInfo());

        List<UUID> ticketIds = ticketJpa.findByPurchaseHistoryId(entity.getId())
            .stream()
            .map(TicketEntity::getId)
            .collect(Collectors.toList());

        return new PurchaseHistory(
            entity.getUserId(),
            ticketIds,
            entity.getEventId(),
            payment,
            entity.getPurchaseDate()
        );
    }
}

