package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.Map;
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
    public void GivenValidMemberAndTicketAmount_WhenRegisterMember_ThenMemberIsRegisteredAndAmountSaved() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 2, LocalDateTime.now());

        assertTrue(lottery.isRegistered("member-1"));
        assertEquals(2, lottery.getRequestedTicketAmount("member-1"));
    }

    @Test
    public void GivenClosedRegistration_WhenRegisterMember_ThenThrowDomainException() {
        PuchaseLottery lottery = createClosedLottery();

        assertThrows(DomainException.class, () ->
                lottery.registerMember("member-1", 2, LocalDateTime.now())
        );
    }

    @Test
    public void GivenDuplicateMember_WhenRegisterMemberTwice_ThenThrowDomainException() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 2, LocalDateTime.now());

        assertThrows(DomainException.class, () ->
                lottery.registerMember("member-1", 3, LocalDateTime.now())
        );

        assertEquals(2, lottery.getRequestedTicketAmount("member-1"));
    }

    @Test
    public void GivenInvalidTicketAmount_WhenRegisterMember_ThenThrowDomainException() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.registerMember("member-1", 0, LocalDateTime.now())
        );

        assertThrows(DomainException.class, () ->
                lottery.registerMember("member-2", -1, LocalDateTime.now())
        );
    }

    @Test
    public void GivenBlankMemberId_WhenRegisterMember_ThenThrowDomainException() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.registerMember("", 1, LocalDateTime.now())
        );

        assertThrows(DomainException.class, () ->
                lottery.registerMember(null, 1, LocalDateTime.now())
        );
    }

    @Test
    public void GivenRegisteredMembers_WhenGetAllRequestedTicketAmounts_ThenReturnCorrectAmounts() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 2, LocalDateTime.now());
        lottery.registerMember("member-2", 3, LocalDateTime.now());

        Map<String, Integer> amounts = lottery.getAllRequestedTicketAmounts();

        assertEquals(2, amounts.size());
        assertEquals(Integer.valueOf(2), amounts.get("member-1"));
        assertEquals(Integer.valueOf(3), amounts.get("member-2"));
    }

    @Test
    public void GivenEnoughTickets_WhenDrawWinners_ThenWinnersAreSelectedAndAccessCodesGenerated() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 2, LocalDateTime.now());
        lottery.registerMember("member-2", 1, LocalDateTime.now());

        LocalDateTime expiry = LocalDateTime.now().plusHours(2);

        lottery.drawWinners(3, expiry);

        assertEquals(2, lottery.getWinnerUsers().size());

        for (String winnerId : lottery.getWinnerUsers()) {
            String accessCode = lottery.getWinnerAccessCode(winnerId);

            assertNotNull(accessCode);
            assertTrue(lottery.isAccessCodeValid(winnerId, accessCode, LocalDateTime.now()));
        }
    }

    @Test
    public void GivenLimitedTickets_WhenDrawWinners_ThenTotalWinningRequestedTicketsDoesNotExceedAvailableTickets() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 2, LocalDateTime.now());
        lottery.registerMember("member-2", 2, LocalDateTime.now());
        lottery.registerMember("member-3", 1, LocalDateTime.now());

        lottery.drawWinners(3, LocalDateTime.now().plusHours(2));

        int totalTicketsForWinners = 0;

        for (String winnerId : lottery.getWinnerUsers()) {
            totalTicketsForWinners += lottery.getRequestedTicketAmount(winnerId);
        }

        assertTrue(totalTicketsForWinners <= 3);
    }

    @Test
    public void GivenNoAvailableTickets_WhenDrawWinners_ThenThrowDomainException() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 1, LocalDateTime.now());

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(0, LocalDateTime.now().plusHours(1))
        );
    }

    @Test
    public void GivenNoRegisteredMembers_WhenDrawWinners_ThenThrowDomainException() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(5, LocalDateTime.now().plusHours(1))
        );
    }

    @Test
    public void GivenNullCodeExpiry_WhenDrawWinners_ThenThrowDomainException() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 1, LocalDateTime.now());

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(5, null)
        );
    }

    @Test
    public void GivenLotteryAlreadyDrawn_WhenDrawWinnersAgain_ThenThrowDomainException() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 1, LocalDateTime.now());

        lottery.drawWinners(1, LocalDateTime.now().plusHours(1));

        assertThrows(DomainException.class, () ->
                lottery.drawWinners(1, LocalDateTime.now().plusHours(1))
        );
    }

    @Test
    public void GivenWinnerWithValidAccessCode_WhenValidateAccessCode_ThenCanBuyTicket() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("winner-member", 1, LocalDateTime.now());

        LocalDateTime expiry = LocalDateTime.now().plusHours(1);
        lottery.drawWinners(1, expiry);

        String accessCode = lottery.getWinnerAccessCode("winner-member");

        assertTrue(lottery.isWinner("winner-member"));
        assertNotNull(accessCode);
        assertTrue(lottery.isAccessCodeValid("winner-member", accessCode, LocalDateTime.now()));
    }

    @Test
    public void GivenNonWinner_WhenValidateAccessCode_ThenCannotBuyTicket() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("winner-member", 1, LocalDateTime.now());
        lottery.registerMember("loser-member", 2, LocalDateTime.now());

        lottery.drawWinners(1, LocalDateTime.now().plusHours(1));

        assertFalse(lottery.isWinner("loser-member"));
        assertNull(lottery.getWinnerAccessCode("loser-member"));

        assertFalse(
                lottery.isAccessCodeValid(
                        "loser-member",
                        "fake-code",
                        LocalDateTime.now()
                )
        );
    }

    @Test
    public void GivenWinnerWithExpiredAccessCode_WhenValidateAccessCode_ThenCannotBuyTicket() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 1, LocalDateTime.now());

        LocalDateTime expiry = LocalDateTime.now().minusMinutes(1);

        lottery.drawWinners(1, expiry);

        String accessCode = lottery.getWinnerAccessCode("member-1");

        assertNotNull(accessCode);
        assertFalse(lottery.isAccessCodeValid("member-1", accessCode, LocalDateTime.now()));
    }

    @Test
    public void GivenWrongAccessCode_WhenValidateAccessCode_ThenCannotBuyTicket() {
        PuchaseLottery lottery = createOpenLottery();

        lottery.registerMember("member-1", 1, LocalDateTime.now());

        lottery.drawWinners(1, LocalDateTime.now().plusHours(1));

        assertFalse(
                lottery.isAccessCodeValid(
                        "member-1",
                        "wrong-code",
                        LocalDateTime.now()
                )
        );
    }

    @Test
    public void GivenUnregisteredMember_WhenGetRequestedTicketAmount_ThenThrowDomainException() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.getRequestedTicketAmount("member-not-registered")
        );
    }

    @Test
    public void GivenUnregisteredMember_WhenAddWinner_ThenThrowDomainException() {
        PuchaseLottery lottery = createOpenLottery();

        assertThrows(DomainException.class, () ->
                lottery.addWinner("member-not-registered")
        );
    }
}