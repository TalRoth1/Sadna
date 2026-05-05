package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.UUID;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PurchaseServiceLotteryTest {

    private PuchaseLottery createOpenLottery() {
        return new PuchaseLottery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1)
        );
    }

    private PuchaseLottery createClosedLottery() {
        return new PuchaseLottery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now().minusHours(3),
                LocalDateTime.now().minusHours(1)
        );
    }

    @Test
    public void SuccessfulLotteryRegistration() {
        PuchaseLottery lottery = createOpenLottery();

        String memberId = "member-1";
        int requestedTickets = 2;

        lottery.registerMember(memberId, requestedTickets, LocalDateTime.now());

        assertTrue(lottery.isRegistered(memberId));
        assertEquals(requestedTickets, lottery.getRequestedTicketAmount(memberId));
        assertEquals(1, lottery.getAllRequestedTicketAmounts().size());
        assertTrue(lottery.getAllRequestedTicketAmounts().containsKey(memberId));
    }

    @Test
    public void LotteryRegistrationUserNotFound() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.registerMember(null, 1, LocalDateTime.now())
        );

        assertThrows(DomainException.class, () ->
                lottery.registerMember("", 1, LocalDateTime.now())
        );

        assertThrows(DomainException.class, () ->
                lottery.registerMember("   ", 1, LocalDateTime.now())
        );

        assertEquals(0, lottery.getAllRequestedTicketAmounts().size());
    }

    @Test
    public void DuplicateLotteryRegistration() {
        PuchaseLottery lottery = createOpenLottery();

        String memberId = "member-1";

        lottery.registerMember(memberId, 2, LocalDateTime.now());

        assertThrows(DomainException.class, () ->
                lottery.registerMember(memberId, 3, LocalDateTime.now())
        );

        assertTrue(lottery.isRegistered(memberId));
        assertEquals(1, lottery.getAllRequestedTicketAmounts().size());
        assertEquals(2, lottery.getRequestedTicketAmount(memberId));
    }

    @Test
    public void UnsuccessfulLotteryResult() {
        PuchaseLottery lottery = createOpenLottery();

        String winnerMemberId = "winner-member";
        String loserMemberId = "loser-member";

        lottery.registerMember(winnerMemberId, 1, LocalDateTime.now());
        lottery.registerMember(loserMemberId, 2, LocalDateTime.now());

        lottery.drawWinners(1, LocalDateTime.now().plusHours(1));

        assertTrue(lottery.isWinner(winnerMemberId));

        assertFalse(lottery.isWinner(loserMemberId));
        assertNull(lottery.getWinnerAccessCode(loserMemberId));

        assertFalse(
                lottery.isAccessCodeValid(
                        loserMemberId,
                        "fake-code",
                        LocalDateTime.now()
                )
        );
    }

    @Test
    public void ClosedLotteryRegistrationShouldFail() {
        PuchaseLottery lottery = createClosedLottery();

        assertThrows(DomainException.class, () ->
                lottery.registerMember("member-1", 1, LocalDateTime.now())
        );

        assertFalse(lottery.isRegistered("member-1"));
    }

    @Test
    public void InvalidTicketAmountShouldFail() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.registerMember("member-1", 0, LocalDateTime.now())
        );

        assertThrows(DomainException.class, () ->
                lottery.registerMember("member-2", -1, LocalDateTime.now())
        );

        assertEquals(0, lottery.getAllRequestedTicketAmounts().size());
    }


    @Test
    public void WinnerCanUseValidAccessCode() {
        PuchaseLottery lottery = createOpenLottery();

        String memberId = "member-1";

        lottery.registerMember(memberId, 1, LocalDateTime.now());

        LocalDateTime expiry = LocalDateTime.now().plusHours(1);
        lottery.drawWinners(1, expiry);

        String accessCode = lottery.getWinnerAccessCode(memberId);

        assertTrue(lottery.isWinner(memberId));
        assertNotNull(accessCode);
        assertTrue(lottery.isAccessCodeValid(memberId, accessCode, LocalDateTime.now()));
    }


    @Test
    public void ExpiredAccessCodeShouldNotBeValid() {
        PuchaseLottery lottery = createOpenLottery();

        String memberId = "member-1";

        lottery.registerMember(memberId, 1, LocalDateTime.now());

        LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(1);
        lottery.drawWinners(1, expiredTime);

        String accessCode = lottery.getWinnerAccessCode(memberId);

        assertNotNull(accessCode);
        assertFalse(lottery.isAccessCodeValid(memberId, accessCode, LocalDateTime.now()));
    }

    @Test
    public void WrongAccessCodeShouldNotBeValid() {
        PuchaseLottery lottery = createOpenLottery();

        String memberId = "member-1";

        lottery.registerMember(memberId, 1, LocalDateTime.now());
        lottery.drawWinners(1, LocalDateTime.now().plusHours(1));

        assertTrue(lottery.isWinner(memberId));
        assertFalse(lottery.isAccessCodeValid(memberId, "wrong-code", LocalDateTime.now()));
    }


    @Test
    public void DrawLotteryWithoutRegisteredMembersShouldFail() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(5, LocalDateTime.now().plusHours(1))
        );
    }

    @Test
    public void DrawLotteryWithNoAvailableTicketsShouldFail() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 1, LocalDateTime.now());

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(0, LocalDateTime.now().plusHours(1))
        );
    }

    @Test
    public void DrawLotteryTwiceShouldFail() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 1, LocalDateTime.now());

        lottery.drawWinners(1, LocalDateTime.now().plusHours(1));

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(1, LocalDateTime.now().plusHours(1))
        );
    }

    @Test
    public void DrawLotteryShouldNotExceedAvailableTickets() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 2, LocalDateTime.now());
        lottery.registerMember("member-2", 2, LocalDateTime.now());
        lottery.registerMember("member-3", 1, LocalDateTime.now());

        int availableTickets = 3;

        lottery.drawWinners(availableTickets, LocalDateTime.now().plusHours(1));

        int totalWinningTickets = 0;

        for (String winnerId : lottery.getWinnerUsers()) {
            totalWinningTickets += lottery.getRequestedTicketAmount(winnerId);
        }

        assertTrue(totalWinningTickets <= availableTickets);
    }
}