package org.example.DomainLayer.LotteryAggregate;

import java.time.LocalDateTime;
import java.util.UUID;

import org.example.DomainLayer.DomainException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PurchaseLotteryTest {

    private PuchaseLottery createOpenLottery() {
        return new PuchaseLottery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
    }

    @Test
    public void constructor_validInput_createsLottery() {
        UUID lotteryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        LocalDateTime open = LocalDateTime.now();
        LocalDateTime close = open.plusDays(1);

        PuchaseLottery lottery = new PuchaseLottery(lotteryId, eventId, open, close);

        assertEquals(lotteryId, lottery.getLotteryId());
        assertEquals(eventId, lottery.getEventId());
        assertEquals(open, lottery.getRegistrationOpen());
        assertEquals(close, lottery.getRegistrationClose());
        assertTrue(lottery.getRegisteredUsers().isEmpty());
        assertTrue(lottery.getWinnerUsers().isEmpty());
    }

    @Test
    public void constructor_nullLotteryId_throwsException() {
        UUID eventId = UUID.randomUUID();
        LocalDateTime open = LocalDateTime.now();
        LocalDateTime close = open.plusDays(1);

        assertThrows(DomainException.class, () ->
                new PuchaseLottery(null, eventId, open, close)
        );
    }

    @Test
    public void constructor_closeBeforeOpen_throwsException() {
        LocalDateTime open = LocalDateTime.now();
        LocalDateTime close = open.minusHours(1);

        assertThrows(DomainException.class, () ->
                new PuchaseLottery(UUID.randomUUID(), UUID.randomUUID(), open, close)
        );
    }

    @Test
    public void isRegistrationOpen_whenNowInsideRange_returnsTrue() {
        PuchaseLottery lottery = createOpenLottery();

        assertTrue(lottery.isRegistrationOpen(LocalDateTime.now()));
    }

    @Test
    public void isRegistrationOpen_whenNowAfterClose_returnsFalse() {
        LocalDateTime open = LocalDateTime.now().minusDays(2);
        LocalDateTime close = LocalDateTime.now().minusDays(1);

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                open,
                close
        );

        assertFalse(lottery.isRegistrationOpen(LocalDateTime.now()));
    }

    @Test
    public void registerMember_validInput_registersMemberAndTicketAmount() {
        PuchaseLottery lottery = createOpenLottery();
        String memberId = "member1";

        lottery.registerMember(memberId, 2, LocalDateTime.now());

        assertTrue(lottery.isRegistered(memberId));
        assertEquals(2, lottery.getRequestedTicketAmount(memberId));
        assertTrue(lottery.getRegisteredUsers().contains(memberId));
    }

    @Test
    public void registerMember_duplicateMember_throwsException() {
        PuchaseLottery lottery = createOpenLottery();
        String memberId = "member1";

        lottery.registerMember(memberId, 2, LocalDateTime.now());

        assertThrows(DomainException.class, () ->
                lottery.registerMember(memberId, 1, LocalDateTime.now())
        );
    }

    @Test
    public void registerMember_ticketAmountZero_throwsException() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.registerMember("member1", 0, LocalDateTime.now())
        );
    }

    @Test
    public void registerMember_whenRegistrationClosed_throwsException() {
        LocalDateTime open = LocalDateTime.now().minusDays(3);
        LocalDateTime close = LocalDateTime.now().minusDays(2);

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                open,
                close
        );

        assertThrows(DomainException.class, () ->
                lottery.registerMember("member1", 1, LocalDateTime.now())
        );
    }

    @Test
    public void getRequestedTicketAmount_memberNotRegistered_throwsException() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.getRequestedTicketAmount("member1")
        );
    }

    @Test
    public void addWinner_registeredMember_addsWinner() {
        PuchaseLottery lottery = createOpenLottery();
        String memberId = "member1";

        lottery.registerMember(memberId, 1, LocalDateTime.now());
        lottery.addWinner(memberId);

        assertTrue(lottery.isWinner(memberId));
        assertTrue(lottery.getWinnerUsers().contains(memberId));
    }

    @Test
    public void addWinner_unregisteredMember_throwsException() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.addWinner("member1")
        );
    }

    @Test
    public void generateWinnerAccessCode_winnerMember_generatesValidCode() {
        PuchaseLottery lottery = createOpenLottery();
        String memberId = "member1";
        LocalDateTime expiry = LocalDateTime.now().plusHours(1);

        lottery.registerMember(memberId, 1, LocalDateTime.now());
        lottery.addWinner(memberId);

        String code = lottery.generateWinnerAccessCode(memberId, expiry);

        assertNotNull(code);
        assertEquals(code, lottery.getWinnerAccessCode(memberId));
        assertTrue(lottery.isAccessCodeValid(memberId, code, LocalDateTime.now()));
    }

    @Test
    public void generateWinnerAccessCode_nonWinner_throwsException() {
        PuchaseLottery lottery = createOpenLottery();
        String memberId = "member1";

        lottery.registerMember(memberId, 1, LocalDateTime.now());

        assertThrows(DomainException.class, () ->
                lottery.generateWinnerAccessCode(memberId, LocalDateTime.now().plusHours(1))
        );
    }

    @Test
    public void isAccessCodeValid_afterExpiry_returnsFalse() {
        PuchaseLottery lottery = createOpenLottery();
        String memberId = "member1";
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(1);

        lottery.registerMember(memberId, 1, LocalDateTime.now());
        lottery.addWinner(memberId);
        String code = lottery.generateWinnerAccessCode(memberId, expiry);

        assertFalse(lottery.isAccessCodeValid(memberId, code, expiry.plusSeconds(1)));
    }

    @Test
    public void drawWinners_validInput_drawsWinnersAndGeneratesCodes() {
        PuchaseLottery lottery = createOpenLottery();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusHours(1);

        lottery.registerMember("member1", 1, now);
        lottery.registerMember("member2", 1, now);
        lottery.registerMember("member3", 1, now);

        lottery.drawWinners(2, expiry);

        assertEquals(2, lottery.getWinnerUsers().size());

        for (String winner : lottery.getWinnerUsers()) {
            String code = lottery.getWinnerAccessCode(winner);
            assertNotNull(code);
            assertTrue(lottery.isAccessCodeValid(winner, code, now));
        }
    }

    @Test
    public void drawWinners_noAvailableTickets_throwsException() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member1", 1, LocalDateTime.now());

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(0, LocalDateTime.now().plusHours(1))
        );
    }

    @Test
    public void drawWinners_noRegisteredUsers_throwsException() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(1, LocalDateTime.now().plusHours(1))
        );
    }

    @Test
    public void drawWinners_calledTwice_throwsException() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member1", 1, LocalDateTime.now());
        lottery.drawWinners(1, LocalDateTime.now().plusHours(1));

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(1, LocalDateTime.now().plusHours(1))
        );
    }
}