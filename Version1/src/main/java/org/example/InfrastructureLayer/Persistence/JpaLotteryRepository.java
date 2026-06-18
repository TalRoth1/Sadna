package org.example.InfrastructureLayer.Persistence;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.example.DomainLayer.ILotteryRepository;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
@Profile("localdb")
@Transactional
public class JpaLotteryRepository implements ILotteryRepository {

@PersistenceContext
private EntityManager entityManager;

@Override
public void save(PuchaseLottery lottery) {
    if (lottery == null) {
        throw new IllegalArgumentException("lottery is required");
    }

    entityManager.createNativeQuery("""
            INSERT INTO lotteries
                (id, registration_open, registration_close)
            VALUES
                (:id, :registrationOpen, :registrationClose)
            ON CONFLICT (id) DO UPDATE SET
                registration_open = EXCLUDED.registration_open,
                registration_close = EXCLUDED.registration_close
            """)
            .setParameter("id", lottery.getLotteryId())
            .setParameter("registrationOpen", lottery.getRegistrationOpen())
            .setParameter("registrationClose", lottery.getRegistrationClose())
            .executeUpdate();

    saveEntries(lottery);
    saveWinners(lottery);

    entityManager.flush();
}

private void saveEntries(PuchaseLottery lottery) {
    entityManager.createNativeQuery("""
            DELETE FROM lottery_entries
            WHERE lottery_id = :lotteryId
            """)
            .setParameter("lotteryId", lottery.getLotteryId())
            .executeUpdate();

    for (Map.Entry<String, Integer> entry : lottery.getAllRequestedTicketAmounts().entrySet()) {
        String memberId = entry.getKey();
        Integer requestedTicketAmount = entry.getValue();

        String username = findUsernameByMemberId(memberId);

        entityManager.createNativeQuery("""
                INSERT INTO lottery_entries
                    (lottery_id, username, requested_ticket_amount)
                VALUES
                    (:lotteryId, :username, :requestedTicketAmount)
                """)
                .setParameter("lotteryId", lottery.getLotteryId())
                .setParameter("username", username)
                .setParameter("requestedTicketAmount", requestedTicketAmount)
                .executeUpdate();
    }
}

private void saveWinners(PuchaseLottery lottery) {
    entityManager.createNativeQuery("""
            DELETE FROM lottery_winners
            WHERE lottery_id = :lotteryId
            """)
            .setParameter("lotteryId", lottery.getLotteryId())
            .executeUpdate();

    for (String memberId : lottery.getWinnerUsers()) {
        String username = findUsernameByMemberId(memberId);
        String accessCode = lottery.getWinnerAccessCode(memberId);
        int requestedTicketAmount = lottery.getRequestedTicketAmount(memberId);

        entityManager.createNativeQuery("""
                INSERT INTO lottery_winners
                    (lottery_id, username, requested_ticket_amount, access_code)
                VALUES
                    (:lotteryId, :username, :requestedTicketAmount, :accessCode)
                """)
                .setParameter("lotteryId", lottery.getLotteryId())
                .setParameter("username", username)
                .setParameter("requestedTicketAmount", requestedTicketAmount)
                .setParameter("accessCode", accessCode)
                .executeUpdate();
    }
}

@Override
@Transactional(readOnly = true)
public PuchaseLottery findByID(UUID lotteryId) {
    if (lotteryId == null) {
        return null;
    }

    List<Object[]> rows = entityManager.createNativeQuery("""
            SELECT l.id,
                   e.id AS event_id,
                   l.registration_open,
                   l.registration_close
            FROM lotteries l
            JOIN events e ON e.lottery_id = l.id
            WHERE l.id = :lotteryId
            LIMIT 1
            """)
            .setParameter("lotteryId", lotteryId)
            .getResultList();

    return rows.isEmpty() ? null : toDomain(rows.get(0));
}

@Override
@Transactional(readOnly = true)
public PuchaseLottery findByEventID(UUID eventId) {
    if (eventId == null) {
        return null;
    }

    List<Object[]> rows = entityManager.createNativeQuery("""
            SELECT l.id,
                   e.id AS event_id,
                   l.registration_open,
                   l.registration_close
            FROM events e
            JOIN lotteries l ON e.lottery_id = l.id
            WHERE e.id = :eventId
            LIMIT 1
            """)
            .setParameter("eventId", eventId)
            .getResultList();

    return rows.isEmpty() ? null : toDomain(rows.get(0));
}

@Override
@Transactional(readOnly = true)
public List<PuchaseLottery> findAll() {
    List<Object[]> rows = entityManager.createNativeQuery("""
            SELECT l.id,
                   e.id AS event_id,
                   l.registration_open,
                   l.registration_close
            FROM lotteries l
            JOIN events e ON e.lottery_id = l.id
            """)
            .getResultList();

    return rows.stream()
            .map(this::toDomain)
            .toList();
}

private PuchaseLottery toDomain(Object[] row) {
    PuchaseLottery lottery = new PuchaseLottery(
            toUuid(row[0]),
            toUuid(row[1]),
            toLocalDateTime(row[2]),
            toLocalDateTime(row[3])
    );

    loadEntries(lottery);
    loadWinners(lottery);

    return lottery;
}

private void loadEntries(PuchaseLottery lottery) {
    List<Object[]> rows = entityManager.createNativeQuery("""
            SELECT u.id, le.requested_ticket_amount
            FROM lottery_entries le
            JOIN users u ON u.username = le.username
            WHERE le.lottery_id = :lotteryId
            """)
            .setParameter("lotteryId", lottery.getLotteryId())
            .getResultList();

    for (Object[] row : rows) {
        String memberId = toUuid(row[0]).toString();
        int requestedTicketAmount = toInt(row[1]);

        if (!lottery.isRegistered(memberId)) {
            lottery.registerMember(
                    memberId,
                    requestedTicketAmount,
                    lottery.getRegistrationOpen()
            );
        }
    }
}

private void loadWinners(PuchaseLottery lottery) {
    List<Object[]> rows = entityManager.createNativeQuery("""
            SELECT u.id
            FROM lottery_winners lw
            JOIN users u ON u.username = lw.username
            WHERE lw.lottery_id = :lotteryId
            """)
            .setParameter("lotteryId", lottery.getLotteryId())
            .getResultList();

    for (Object[] row : rows) {
        String memberId = toUuid(row[0]).toString();

        if (lottery.isRegistered(memberId) && !lottery.isWinner(memberId)) {
            lottery.addWinner(memberId);
        }
    }
}

private String findUsernameByMemberId(String memberId) {
    if (memberId == null || memberId.isBlank()) {
        throw new IllegalArgumentException("memberId is required");
    }

    UUID userId;
    try {
        userId = UUID.fromString(memberId);
    } catch (IllegalArgumentException e) {
        /*
         * In case older code already passes username instead of UUID,
         * keep supporting it.
         */
        return memberId;
    }

    List<?> rows = entityManager.createNativeQuery("""
            SELECT username
            FROM users
            WHERE id = :userId
            LIMIT 1
            """)
            .setParameter("userId", userId)
            .getResultList();

    if (rows.isEmpty()) {
        throw new IllegalArgumentException("User not found for id: " + memberId);
    }

    return rows.get(0).toString();
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

private int toInt(Object value) {
    if (value == null) {
        return 0;
    }

    if (value instanceof Number number) {
        return number.intValue();
    }

    return Integer.parseInt(value.toString());
}

@Override
@Transactional(readOnly = true)
public List<UUID> findEventIdsReadyForDraw(LocalDateTime now) {
    List<?> rows = entityManager.createNativeQuery("""
            SELECT e.id
            FROM events e
            JOIN lotteries l ON e.lottery_id = l.id
            WHERE l.registration_close <= :now
              AND EXISTS (
                    SELECT 1
                    FROM lottery_entries le
                    WHERE le.lottery_id = l.id
              )
              AND NOT EXISTS (
                    SELECT 1
                    FROM lottery_winners lw
                    WHERE lw.lottery_id = l.id
              )
            """)
            .setParameter("now", now)
            .getResultList();

    return rows.stream()
            .map(this::toUuid)
            .toList();
}

}
