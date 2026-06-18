package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.IPurchaseRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@Profile("localdb")
@Transactional
public class JpaPurchaseRepository implements IPurchaseRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public ActivePurchase findByUserID(UUID userID) {
        if (userID == null) {
            return null;
        }

        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT id,
                               user_id,
                               event_id,
                               end_time,
                               is_guest_confirmed_age,
                               copon,
                               price,
                               max_wait_time,
                               last_updated
                        FROM active_purchases
                        WHERE user_id = :userId
                        ORDER BY last_updated DESC
                        LIMIT 1
                        """)
                .setParameter("userId", userID)
                .getResultList();

        return rows.isEmpty() ? null : toDomain(rows.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public ActivePurchase findByUserAndEvent(UUID userID, UUID eventID) {
        if (userID == null || eventID == null) {
            return null;
        }

        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT id,
                               user_id,
                               event_id,
                               end_time,
                               is_guest_confirmed_age,
                               copon,
                               price,
                               max_wait_time,
                               last_updated
                        FROM active_purchases
                        WHERE user_id = :userId
                          AND event_id = :eventId
                        ORDER BY last_updated DESC
                        LIMIT 1
                        """)
                .setParameter("userId", userID)
                .setParameter("eventId", eventID)
                .getResultList();

        return rows.isEmpty() ? null : toDomain(rows.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public ActivePurchase findByID(UUID purchaseID) {
        if (purchaseID == null) {
            return null;
        }

        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT id,
                               user_id,
                               event_id,
                               end_time,
                               is_guest_confirmed_age,
                               copon,
                               price,
                               max_wait_time,
                               last_updated
                        FROM active_purchases
                        WHERE id = :purchaseId
                        LIMIT 1
                        """)
                .setParameter("purchaseId", purchaseID)
                .getResultList();

        return rows.isEmpty() ? null : toDomain(rows.get(0));
    }

    @Override
    public void save(ActivePurchase activePurchase) {
        if (activePurchase == null) {
            throw new IllegalArgumentException("activePurchase is required");
        }

        LocalDateTime now = LocalDateTime.now();
        setPrivateField(activePurchase, "lastUpdate", now);

        entityManager.createNativeQuery("""
                        INSERT INTO active_purchases
                            (id, user_id, event_id, end_time, is_guest_confirmed_age,
                             copon, price, max_wait_time, last_updated)
                        VALUES
                            (:id, :userId, :eventId, :endTime, :isGuestConfirmedAge,
                             :copon, :price, :maxWaitTime, :lastUpdated)
                        ON CONFLICT (id) DO UPDATE SET
                            user_id = EXCLUDED.user_id,
                            event_id = EXCLUDED.event_id,
                            end_time = EXCLUDED.end_time,
                            is_guest_confirmed_age = EXCLUDED.is_guest_confirmed_age,
                            copon = EXCLUDED.copon,
                            price = EXCLUDED.price,
                            max_wait_time = EXCLUDED.max_wait_time,
                            last_updated = EXCLUDED.last_updated
                        """)
                .setParameter("id", activePurchase.getActivePurchaseId())
                .setParameter("userId", activePurchase.getUserID())
                .setParameter("eventId", activePurchase.getEventID())
                .setParameter("endTime", activePurchase.getEndTime())
                .setParameter("isGuestConfirmedAge", activePurchase.getGuestAgeConfirmed())
                .setParameter("copon", activePurchase.getCoupon())
                .setParameter("price", (double) activePurchase.getPrice())
                .setParameter("maxWaitTime", (double) activePurchase.getMaxWaitTime())
                .setParameter("lastUpdated", activePurchase.getLastUpdate())
                .executeUpdate();

        syncReservedTickets(activePurchase);
    }

    @Override
    public void deleteByID(UUID activePurchaseID) {
        if (activePurchaseID == null) {
            return;
        }

        // Cancellation/expiry path: release every ticket that still belongs only
        // to this active purchase. Tickets already moved into purchase_history
        // are not touched.
        entityManager.createNativeQuery("""
                        UPDATE tickets
                        SET status = 'AVAILABLE',
                            active_purchase_id = NULL,
                            user_id = NULL
                        WHERE active_purchase_id = :activePurchaseId
                          AND purchase_history_id IS NULL
                          AND status <> 'SOLD'
                        """)
                .setParameter("activePurchaseId", activePurchaseID)
                .executeUpdate();

        entityManager.createNativeQuery("""
                        DELETE FROM active_purchases
                        WHERE id = :activePurchaseId
                        """)
                .setParameter("activePurchaseId", activePurchaseID)
                .executeUpdate();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivePurchase> findAll() {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT id,
                               user_id,
                               event_id,
                               end_time,
                               is_guest_confirmed_age,
                               copon,
                               price,
                               max_wait_time,
                               last_updated
                        FROM active_purchases
                        ORDER BY last_updated DESC
                        """)
                .getResultList();

        List<ActivePurchase> purchases = new ArrayList<>();
        for (Object[] row : rows) {
            purchases.add(toDomain(row));
        }
        return purchases;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivePurchase> findExpiringBefore(java.time.LocalDateTime threshold) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT id,
                               user_id,
                               event_id,
                               end_time,
                               is_guest_confirmed_age,
                               copon,
                               price,
                               max_wait_time,
                               last_updated
                        FROM active_purchases
                        WHERE end_time < :threshold
                        ORDER BY last_updated DESC
                        """)
                .setParameter("threshold", threshold)
                .getResultList();

        List<ActivePurchase> purchases = new ArrayList<>();
        for (Object[] row : rows) {
            purchases.add(toDomain(row));
        }
        return purchases;
    }

    private void syncReservedTickets(ActivePurchase activePurchase) {
        UUID activePurchaseId = activePurchase.getActivePurchaseId();
        List<UUID> ticketIds = new ArrayList<>(activePurchase.getTicketIDs().keySet());

        if (ticketIds.isEmpty()) {
            entityManager.createNativeQuery("""
                            UPDATE tickets
                            SET status = 'AVAILABLE',
                                active_purchase_id = NULL,
                                user_id = NULL
                            WHERE active_purchase_id = :activePurchaseId
                              AND purchase_history_id IS NULL
                            """)
                    .setParameter("activePurchaseId", activePurchaseId)
                    .executeUpdate();
            return;
        }

        entityManager.createNativeQuery("""
                        UPDATE tickets
                        SET status = 'AVAILABLE',
                            active_purchase_id = NULL,
                            user_id = NULL
                        WHERE active_purchase_id = :activePurchaseId
                          AND purchase_history_id IS NULL
                          AND id NOT IN (:ticketIds)
                        """)
                .setParameter("activePurchaseId", activePurchaseId)
                .setParameter("ticketIds", ticketIds)
                .executeUpdate();

        Query reserveQuery = entityManager.createNativeQuery("""
                        UPDATE tickets
                        SET status = 'RESERVED',
                            active_purchase_id = :activePurchaseId,
                            purchase_history_id = NULL,
                            user_id = :userId
                        WHERE id IN (:ticketIds)
                          AND event_id = :eventId
                          AND purchase_history_id IS NULL
                          AND (status = 'AVAILABLE' OR active_purchase_id = :activePurchaseId)
                        """)
                .setParameter("activePurchaseId", activePurchaseId)
                .setParameter("userId", activePurchase.getUserID())
                .setParameter("eventId", activePurchase.getEventID())
                .setParameter("ticketIds", ticketIds);

        int updatedRows = reserveQuery.executeUpdate();
        if (updatedRows != ticketIds.size()) {
            throw new IllegalStateException("One or more selected tickets are not available");
        }
    }

    private ActivePurchase toDomain(Object[] row) {
        UUID id = toUuid(row[0]);
        UUID userId = toUuid(row[1]);
        UUID eventId = toUuid(row[2]);
        LocalDateTime endTime = toLocalDateTime(row[3]);
        boolean isGuestConfirmedAge = toBoolean(row[4]);
        String coupon = row[5] == null ? "" : row[5].toString();
        float price = toFloat(row[6]);
        float maxWaitTime = toFloat(row[7]);
        LocalDateTime lastUpdated = toLocalDateTime(row[8]);

        LinkedHashMap<UUID, Float> ticketPrices = loadTicketPrices(id);

        ActivePurchase activePurchase = new ActivePurchase(
                userId,
                eventId,
                ticketPrices,
                endTime,
                maxWaitTime
        );

        activePurchase.SetGuestAgeConfirmed(isGuestConfirmedAge);
        activePurchase.setCoupon(coupon);
        activePurchase.setPrice(price);

        // ActivePurchase currently has no DB-hydration constructor/setters for
        // id and lastUpdate, so the repository restores those two fields here.
        setPrivateField(activePurchase, "id", id);
        setPrivateField(activePurchase, "lastUpdate", lastUpdated);

        return activePurchase;
    }

    private LinkedHashMap<UUID, Float> loadTicketPrices(UUID activePurchaseId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT id, price
                        FROM tickets
                        WHERE active_purchase_id = :activePurchaseId
                        ORDER BY id
                        """)
                .setParameter("activePurchaseId", activePurchaseId)
                .getResultList();

        LinkedHashMap<UUID, Float> ticketPrices = new LinkedHashMap<>();
        for (Object[] row : rows) {
            ticketPrices.put(toUuid(row[0]), toFloat(row[1]));
        }
        return ticketPrices;
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return Timestamp.valueOf(value.toString()).toLocalDateTime();
    }

    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private float toFloat(Object value) {
        if (value == null) {
            return 0.0f;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return Float.parseFloat(value.toString());
    }

    private void setPrivateField(ActivePurchase activePurchase, String fieldName, Object value) {
        try {
            Field field = ActivePurchase.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(activePurchase, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to restore ActivePurchase field: " + fieldName, e);
        }
    }
}
