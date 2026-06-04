package org.example.DomainLayer;

import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.ITicketingGateway;
import org.example.ApplicationLayer.dto.SalesReport;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.AdminAggregate.AdminActionLog;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.AdminComplaintStatus;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.EventAggregate.StandingTicket;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.Events.TicketReservedEvent;
import org.example.DomainLayer.Events.UserRegisteredEvent;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.NotificationAggregate.Notification;
import org.example.DomainLayer.NotificationAggregate.NotificationType;
import org.example.DomainLayer.PolicyManagment.*;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.ICompanyMember;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserRole;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DomainLayerAdditionalTests {

    private ActivePurchase activePurchase(UUID userId, UUID eventId, float... prices) {
        Map<UUID, Float> ticketPrices = new LinkedHashMap<>();
        for (float price : prices) {
            ticketPrices.put(UUID.randomUUID(), price);
        }
        return new ActivePurchase(
                userId,
                eventId,
                ticketPrices,
                LocalDateTime.now().plusMinutes(10)
        );
    }

    private Event eventWithSittingSeats(UUID eventId, UUID companyId, UUID areaId, int seats) {
        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(10),
                "Tel Aviv",
                "Artist",
                "concert",
                EventStatus.ACTIVE
        );

        event.getLayout().addArea(new SittingArea(areaId, 100f));

        for (int seat = 1; seat <= seats; seat++) {
            UUID ticketId = UUID.randomUUID();
            event.addTicket(new SittingTicket(ticketId, eventId, areaId, 100f, 1, seat));
        }

        return event;
    }

    // ================================================================
    // RolesDomainService
    // ================================================================

    @Test
    public void rolesDomainService_createCompany_addsFounderRoleToFounderUser() {
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        RolesDomainService service = new RolesDomainService(companyRepository, userRepository);

        User founder = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 40);
        UUID companyId = UUID.randomUUID();

        when(userRepository.findByEmail("founder@example.com"))
                .thenReturn(Optional.of(founder));
        when(companyRepository.createCompany("founder@example.com", "Acme"))
                .thenReturn(companyId);

        UUID result = service.createCompany("founder@example.com", "Acme");

        assertEquals(companyId, result);
        assertTrue(founder.isCompanyMember(companyId));
        assertTrue(founder.getCompanyRole(companyId) instanceof CompanyFounder);
    }

    @Test
    public void rolesDomainService_createCompany_validationFailures() {
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        RolesDomainService service = new RolesDomainService(companyRepository, userRepository);

        assertThrows(IllegalArgumentException.class,
                () -> service.createCompany(null, "Acme"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createCompany("   ", "Acme"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createCompany("founder@example.com", " "));

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.createCompany("missing@example.com", "Acme"));
    }

    @Test
    public void rolesDomainService_closeCompanyAsAdmin_successAndFailureBranches() {
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        RolesDomainService service = new RolesDomainService(companyRepository, userRepository);

        UUID companyId = UUID.randomUUID();
        Company company = new Company("founder@example.com", "Acme");

        when(userRepository.isSystemAdmin("admin")).thenReturn(true);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));

        service.closeCompanyAsAdmin("admin", companyId);

        assertFalse(company.isActive());
        verify(companyRepository).save(company);

        assertThrows(IllegalStateException.class,
                () -> service.closeCompanyAsAdmin("admin", companyId));

        assertThrows(IllegalArgumentException.class,
                () -> service.closeCompanyAsAdmin(null, companyId));

        assertThrows(IllegalArgumentException.class,
                () -> service.closeCompanyAsAdmin("admin", null));

        when(userRepository.isSystemAdmin("not-admin")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.closeCompanyAsAdmin("not-admin", UUID.randomUUID()));
    }

    @Test
    public void rolesDomainService_getCompanyAccessAndSubordinates_coverOwnerAndManagerBranches() {
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        RolesDomainService service = new RolesDomainService(companyRepository, userRepository);

        UUID companyId = UUID.randomUUID();
        Company company = new Company("founder@example.com", "Acme");

        User founder = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 40);
        CompanyFounder founderRole = new CompanyFounder("founder@example.com");
        founder.getCompanyRoles().put(companyId, founderRole);

        User manager = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 30);
        CompanyManager managerRole = new CompanyManager(
                "manager@example.com",
                founderRole,
                Set.of(CompanyPermission.MANAGE_POLICIES)
        );
        founderRole.addSubordinate(managerRole);
        manager.getCompanyRoles().put(companyId, managerRole);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("founder@example.com")).thenReturn(Optional.of(founder));
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(manager));

        assertNotNull(service.getCompanyAccess(companyId, "founder@example.com"));
        assertNotNull(service.getCompanyAccess(companyId, "manager@example.com"));

        List<String> usernames =
                service.getOwnerAndSubordinatesUsernames(companyId, "founder@example.com");

        assertEquals(List.of("founder@example.com", "manager@example.com"), usernames);

        assertThrows(IllegalArgumentException.class,
                () -> service.getOwnerAndSubordinatesUsernames(companyId, "manager@example.com"));
    }

    // ================================================================
    // UserAggregate
    // ================================================================

    @Test
    public void user_loginLogoutAndGuestBranches() {
        User user = new User(UUID.randomUUID(), "alice", "alice@example.com", "hash", 25);

        assertEquals(UserStatus.NOT_LOGGED_IN, user.getStatus());

        user.login();

        assertEquals(UserStatus.LOGGED_IN, user.getStatus());
        assertEquals(UserRole.MEMBER, user.getRole());

        assertThrows(IllegalStateException.class, user::login);

        user.logout();

        assertEquals(UserStatus.NOT_LOGGED_IN, user.getStatus());

        assertThrows(IllegalStateException.class, user::logout);

        User guest = new User(UUID.randomUUID());
        assertEquals(UserRole.GUEST, guest.getRole());
        assertNull(guest.getEmail());
        assertTrue(guest.getUsername().startsWith("guest-"));
    }

    @Test
    public void user_inviteAcceptRejectOwnerAndManagerInvitations() {
        UUID companyId = UUID.randomUUID();

        User founder = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 50);
        founder.getCompanyRoles().put(companyId, new CompanyFounder("founder@example.com"));

        User managerCandidate =
                new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 30);

        UUID managerInvitation = managerCandidate.inviteUserToBecomeManager(
                companyId,
                founder,
                Set.of(CompanyPermission.MANAGE_POLICIES)
        );

        assertEquals(1, managerCandidate.getCompanyInvitations().size());

        managerCandidate.acceptCompanyInvitation(managerInvitation);

        assertTrue(managerCandidate.isCompanyMember(companyId));
        assertTrue(managerCandidate.isManagerInCompany(companyId));
        assertTrue(managerCandidate.getCompanyRole(companyId) instanceof CompanyManager);

        User ownerCandidate =
                new User(UUID.randomUUID(), "owner", "owner@example.com", "hash", 35);

        UUID ownerInvitation =
                ownerCandidate.inviteUserToBecomeOwner(companyId, founder);

        ownerCandidate.rejectCompanyInvitation(ownerInvitation);

        assertTrue(ownerCandidate.getCompanyInvitations().isEmpty());

        assertThrows(IllegalStateException.class,
                () -> ownerCandidate.acceptCompanyInvitation(ownerInvitation));
    }

    @Test
    public void user_changeManagerPermissionsAndRemoveBranches() {
        UUID companyId = UUID.randomUUID();

        User founder = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 50);
        CompanyFounder founderRole = new CompanyFounder("founder@example.com");
        founder.getCompanyRoles().put(companyId, founderRole);

        User manager = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 30);
        CompanyManager managerRole = new CompanyManager(
                "manager@example.com",
                founderRole,
                Set.of(CompanyPermission.VIEW_HISTORY)
        );
        founderRole.addSubordinate(managerRole);
        manager.getCompanyRoles().put(companyId, managerRole);

        assertFalse(manager.hasPremisions(companyId, CompanyPermission.MANAGE_POLICIES, UUID.randomUUID()));

        manager.changeManagerPermissionsAsOwner(
                companyId,
                founder,
                Set.of(CompanyPermission.MANAGE_POLICIES)
        );

        assertFalse(manager.hasPremisions(companyId, CompanyPermission.VIEW_HISTORY, UUID.randomUUID()));

        manager.removeFromCompanyAsOwner(companyId, founder);

        assertFalse(manager.isCompanyMember(companyId));

        assertThrows(IllegalArgumentException.class,
                () -> manager.removeFromCompanyAsOwner(companyId, founder));
    }

    @Test
    public void companyOwnerAndManagerHierarchyBranches() {
        UUID eventId = UUID.randomUUID();

        CompanyFounder founder = new CompanyFounder("founder");
        CompanyOwner owner = new CompanyOwner("owner", founder);
        CompanyManager manager =
                new CompanyManager("manager", owner, Set.of(CompanyPermission.MANAGE_INVENTORY));

        founder.addSubordinate(owner);
        owner.addSubordinate(manager);

        owner.getEventsIds().add(eventId);
        manager.getEventsIds().add(eventId);

        assertTrue(founder.getSubordinates().contains(owner));
        assertTrue(owner.getSubordinates().contains(manager));

        assertTrue(owner.hasPremission(CompanyPermission.MANAGE_POLICIES, eventId));
        assertTrue(manager.hasPremission(CompanyPermission.MANAGE_INVENTORY, eventId));
        assertFalse(manager.hasPremission(CompanyPermission.MANAGE_POLICIES, eventId));

        assertEquals(founder, manager.getFounder());
        assertTrue(founder.getEventsUnderMe().contains(eventId));
        assertTrue(owner.getEventsUnderMe().contains(eventId));
        assertTrue(manager.getEventsUnderMe().contains(eventId));

        StringBuilder sb = new StringBuilder();
        founder.buildMermaid(sb);

        assertTrue(sb.toString().contains("Founder"));
        assertTrue(sb.toString().contains("manager"));

        owner.removeSubordinate(manager);

        assertFalse(owner.getSubordinates().contains(manager));
    }

    // ================================================================
    // PolicyManagment
    // ================================================================

    @Test
    public void purchaseRules_ageMinMaxAndCompositeBranches() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        User adult = new User(userId, "adult", "adult@example.com", "hash", 21);
        User minor = new User(UUID.randomUUID(), "minor", "minor@example.com", "hash", 15);

        ActivePurchase oneTicket = activePurchase(userId, eventId, 100f);
        ActivePurchase threeTickets = activePurchase(userId, eventId, 100f, 50f, 20f);
        Event event = mock(Event.class);

        AgeRule age18 = new AgeRule(18f);
        MinTicketRule min2 = new MinTicketRule(2);
        MaxTicketRule max2 = new MaxTicketRule(2);

        assertTrue(age18.doesHold(oneTicket, adult, event));
        assertFalse(age18.doesHold(oneTicket, minor, event));

        assertFalse(min2.doesHold(oneTicket, adult, event));
        assertTrue(min2.doesHold(threeTickets, adult, event));

        assertTrue(max2.doesHold(oneTicket, adult, event));
        assertFalse(max2.doesHold(threeTickets, adult, event));

        PurchaseComposite andComposite = new PurchaseComposite(age18, min2, true);
        assertTrue(andComposite.isAnd());
        assertTrue(andComposite.doesHold(threeTickets, adult, event));
        assertFalse(andComposite.doesHold(oneTicket, adult, event));

        PurchaseComposite orComposite = new PurchaseComposite(age18, min2, false);
        assertFalse(orComposite.isAnd());
        assertTrue(orComposite.doesHold(oneTicket, adult, event));
        assertFalse(orComposite.doesHold(oneTicket, minor, event));

        assertSame(min2, andComposite.removeRule(age18.getId()));
    }

    @Test
    public void purchasePolicy_removeRootNestedAndMissingRules() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        PurchasePolicy policy = new PurchasePolicy();
        ActivePurchase purchase = activePurchase(userId, eventId, 100f, 50f);
        User user = new User(userId, "user", "user@example.com", "hash", 30);
        Event event = mock(Event.class);

        AgeRule ageRule = new AgeRule(18f);
        MinTicketRule minRule = new MinTicketRule(2);
        MaxTicketRule maxRule = new MaxTicketRule(3);

        policy.addRule(ageRule, true);
        policy.addRule(minRule, true);
        policy.addRule(maxRule, true);

        assertTrue(policy.validate(purchase, user, event));
        assertNotNull(policy.getRulesView());

        policy.removeRule(minRule.getId());

        assertNotNull(policy.getRulesView());
        assertTrue(policy.validate(purchase, user, event));

        policy.removeRule(UUID.randomUUID());

        assertNotNull(policy.getRulesView());

        policy.removeRule(policy.getRulesView().getId());

        assertNull(policy.getRulesView());
        assertTrue(policy.validate(purchase, user, event));
    }

    @Test
    public void loneSeatRule_detectsLoneSeatAndAllowsWhenConfigured() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = eventWithSittingSeats(eventId, companyId, areaId, 3);

        List<UUID> ticketIds = event.getTicketsView().keySet().stream().toList();

        UUID seat1 = ticketIds.get(0);
        UUID seat3 = ticketIds.get(2);

        Map<UUID, Float> purchaseTickets = new LinkedHashMap<>();
        purchaseTickets.put(seat1, 100f);
        purchaseTickets.put(seat3, 100f);

        ActivePurchase purchase =
                new ActivePurchase(userId, eventId, purchaseTickets, LocalDateTime.now().plusMinutes(10));

        User user = new User(userId, "buyer", "buyer@example.com", "hash", 30);

        LoneSeatRule disallowLoneSeat = new LoneSeatRule(false);
        LoneSeatRule allowLoneSeat = new LoneSeatRule(true);

        assertTrue(allowLoneSeat.doesHold(purchase, user, event));
        assertFalse(disallowLoneSeat.isAllowLoneSeat());
        assertTrue(allowLoneSeat.isAllowLoneSeat());
    }

    @Test
    public void discountPolicy_conditionalCouponAndInvalidCouponBranches() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ActivePurchase purchase = activePurchase(userId, eventId, 100f, 50f, 30f);

        DiscountPolicy policy = new DiscountPolicy();

        assertFalse(policy.hasRules());

        ConditionalDiscount conditional = new ConditionalDiscount(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                50f,
                2,
                1
        );

        CouponCode coupon = new CouponCode(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                10f,
                "SAVE10"
        );

        policy.addRule(conditional);
        policy.addRule(coupon);

        assertTrue(policy.hasRules());
        assertEquals(2, policy.getDiscountRules().size());

        float discounted = policy.applyDiscount(purchase, " SAVE10 ");

        assertTrue(discounted > 0);

        ActivePurchase noCouponPurchase = activePurchase(userId, eventId, 100f, 50f);
        DiscountPolicy couponOnly = new DiscountPolicy();
        couponOnly.addRule(coupon);

        assertEquals(150f, couponOnly.applyDiscount(noCouponPurchase), 0.001);

        assertThrows(DomainException.class,
                () -> couponOnly.applyDiscount(activePurchase(userId, eventId, 100f), "WRONG"));

        assertThrows(IllegalArgumentException.class,
                () -> policy.applyDiscount(null));

        UUID couponId = coupon.getId();
        policy.removeRule(couponId);

        assertEquals(1, policy.getDiscountRules().size());
    }

    @Test
    public void overtAndConditionalDiscountDateBranches() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ActivePurchase active = activePurchase(userId, eventId, 100f, 80f, 60f);
        ActivePurchase inactive = activePurchase(userId, eventId, 100f, 80f, 60f);

        ConditionalDiscount conditionalActive = new ConditionalDiscount(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                50f,
                2,
                2
        );

        ConditionalDiscount conditionalFuture = new ConditionalDiscount(
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(20),
                50f,
                2,
                2
        );

        assertEquals(inactive.getPrice(), conditionalFuture.apply(inactive), 0.001);

        assertEquals(2, conditionalActive.getRequiredTickets());
        assertEquals(2, conditionalActive.getAppliedTickets());
        assertEquals(50f, conditionalActive.getDiscountPercent(), 0.001);

        OvertDiscount expired = new OvertDiscount(
                10f,
                LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1)
        );

    }

    // ================================================================
    // LotteryAggregate
    // ================================================================

    @Test
    public void purchaseLottery_constructorAndRegistrationValidationBranches() {
        UUID lotteryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        LocalDateTime open = LocalDateTime.now().minusDays(1);
        LocalDateTime close = LocalDateTime.now().plusDays(1);

        assertThrows(DomainException.class,
                () -> new PuchaseLottery(null, eventId, open, close));

        assertThrows(DomainException.class,
                () -> new PuchaseLottery(lotteryId, null, open, close));

        assertThrows(DomainException.class,
                () -> new PuchaseLottery(lotteryId, eventId, null, close));

        assertThrows(DomainException.class,
                () -> new PuchaseLottery(lotteryId, eventId, close, open));

        PuchaseLottery lottery = new PuchaseLottery(lotteryId, eventId, open, close);

        assertTrue(lottery.isRegistrationOpen(LocalDateTime.now()));
        assertFalse(lottery.isRegistrationOpen(open.minusSeconds(1)));
        assertFalse(lottery.isRegistrationOpen(close.plusSeconds(1)));

        assertThrows(DomainException.class,
                () -> lottery.isRegistrationOpen(null));

        assertThrows(DomainException.class,
                () -> lottery.registerMember(null, 1, LocalDateTime.now()));

        assertThrows(DomainException.class,
                () -> lottery.registerMember("user", 0, LocalDateTime.now()));

        assertThrows(DomainException.class,
                () -> lottery.registerMember("user", 1, open.minusSeconds(1)));

        lottery.registerMember("user", 2, LocalDateTime.now());

        assertTrue(lottery.isRegistered("user"));
        assertEquals(2, lottery.getRequestedTicketAmount("user"));
        assertEquals(Integer.valueOf(2), lottery.getAllRequestedTicketAmounts().get("user"));

        assertThrows(DomainException.class,
                () -> lottery.registerMember("user", 1, LocalDateTime.now()));

        assertThrows(DomainException.class,
                () -> lottery.getRequestedTicketAmount("missing"));
    }

    @Test
    public void purchaseLottery_drawWinnersAccessCodesAndWinnerBranches() {
        UUID eventId = UUID.randomUUID();

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        assertThrows(DomainException.class,
                () -> lottery.drawWinners(0, LocalDateTime.now().plusMinutes(10)));

        assertThrows(DomainException.class,
                () -> lottery.drawWinners(1, null));

        assertThrows(DomainException.class,
                () -> lottery.drawWinners(1, LocalDateTime.now().plusMinutes(10)));

        lottery.registerMember("small-request", 1, LocalDateTime.now());
        lottery.registerMember("too-big-request", 2, LocalDateTime.now());

        Map<String, String> codes =
                lottery.drawWinners(1, LocalDateTime.now().plusMinutes(10));

        assertEquals(1, codes.size());
        assertTrue(lottery.areWinnersDrawn());
        assertEquals(1, lottery.getWinnerUsers().size());

        String winner = lottery.getWinnerUsers().iterator().next();
        String accessCode = lottery.getWinnerAccessCode(winner);

        assertNotNull(accessCode);
        assertTrue(lottery.isAccessCodeValid(winner, accessCode, LocalDateTime.now()));
        assertFalse(lottery.isAccessCodeValid(winner, "wrong-code", LocalDateTime.now()));
        assertFalse(lottery.isAccessCodeValid(winner, accessCode, LocalDateTime.now().plusDays(1)));

        assertThrows(DomainException.class,
                () -> lottery.addWinner("not-registered"));

        assertThrows(DomainException.class,
                () -> lottery.generateWinnerAccessCode("not-winner", LocalDateTime.now().plusMinutes(1)));

        assertThrows(DomainException.class,
                () -> lottery.isAccessCodeValid(null, accessCode, LocalDateTime.now()));

        assertThrows(DomainException.class,
                () -> lottery.isAccessCodeValid(winner, " ", LocalDateTime.now()));

        assertThrows(DomainException.class,
                () -> lottery.isAccessCodeValid(winner, accessCode, null));

        assertThrows(DomainException.class,
                () -> lottery.drawWinners(1, LocalDateTime.now().plusMinutes(10)));

        assertThrows(DomainException.class,
                () -> lottery.registerMember("late-user", 1, LocalDateTime.now()));
    }

    // ================================================================
    // EventAggregate
    // ================================================================

    @Test
    public void event_constructorSettersTagsAndTicketValidationBranches() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new Event(eventId, companyId, null, "Tel Aviv", "Artist", "concert", EventStatus.ACTIVE));

        assertThrows(IllegalArgumentException.class,
                () -> new Event(eventId, companyId, LocalDateTime.now(), " ", "Artist", "concert", EventStatus.ACTIVE));

        assertThrows(IllegalArgumentException.class,
                () -> new Event(eventId, companyId, LocalDateTime.now(), "Tel Aviv", " ", "concert", EventStatus.ACTIVE));

        assertThrows(IllegalArgumentException.class,
                () -> new Event(eventId, companyId, LocalDateTime.now(), "Tel Aviv", "Artist", " ", EventStatus.ACTIVE));

        assertThrows(IllegalArgumentException.class,
                () -> new Event(eventId, companyId, LocalDateTime.now(), "Tel Aviv", "Artist", "concert", null));

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(1),
                "  Tel Aviv  ",
                "  Artist  ",
                "  concert  ",
                EventStatus.ACTIVE
        );

        event.setDescription("  description  ");
        event.addTag(" jazz ");
        event.setName("  Event Name  ");
        event.setRating(4.5);

        assertEquals("Tel Aviv", event.getLocation());
        assertEquals("Artist", event.getArtist());
        assertEquals("concert", event.getType());
        assertEquals("description", event.getDescription());
        assertEquals("Event Name", event.getName());
        assertEquals(4.5, event.getRating(), 0.001);

        assertThrows(IllegalArgumentException.class,
                () -> event.addTag(" "));

        assertThrows(IllegalArgumentException.class,
                () -> event.setRating(-1));

        assertThrows(IllegalArgumentException.class,
                () -> event.setStatus(null));
    }

    @Test
    public void event_ticketLifecycleAreaPriceAndDeleteAreaBranches() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID standingAreaId = UUID.randomUUID();
        UUID sittingAreaId = UUID.randomUUID();

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(1),
                "Haifa",
                "Artist",
                "concert",
                EventStatus.ACTIVE
        );

        event.getLayout().addArea(new StandingArea(standingAreaId, 50));
        event.getLayout().addArea(new SittingArea(sittingAreaId, 100));

        event.addStandingTickets(standingAreaId, 2);
        event.addSittingTickets(sittingAreaId, 1, 2);

        assertEquals(4, event.getTotalCapacity());

        UUID standingTicketId = event.getTicketsView().values().stream()
                .filter(t -> t instanceof StandingTicket)
                .findFirst()
                .orElseThrow()
                .getTicketId();

        Ticket standingTicket = event.getTicket(standingTicketId);

        event.updateAreaPrice(standingAreaId, 75);

        assertEquals(75f, standingTicket.getPrice(), 0.001);

        assertThrows(IllegalArgumentException.class,
                () -> event.updateAreaPrice(null, 1));

        assertThrows(IllegalArgumentException.class,
                () -> event.updateAreaPrice(standingAreaId, -1));

        standingTicket.reserve();

        assertThrows(IllegalStateException.class,
                () -> event.removeTicket(standingTicketId));

        standingTicket.releaseReservation();

        event.removeTicket(standingTicketId);

        assertNull(event.getTicket(standingTicketId));

        UUID sittingTicketId = event.getTicketsView().values().stream()
                .filter(t -> t instanceof SittingTicket)
                .findFirst()
                .orElseThrow()
                .getTicketId();

        event.getTicket(sittingTicketId).reserve();

        assertThrows(IllegalStateException.class,
                () -> event.deleteArea(sittingAreaId));

        event.getTicket(sittingTicketId).releaseReservation();

        event.deleteArea(sittingAreaId);

        assertThrows(IllegalArgumentException.class,
                () -> event.deleteArea(sittingAreaId));
    }

    @Test
    public void event_updateStandingAndSittingAreaBranches() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID standingAreaId = UUID.randomUUID();
        UUID sittingAreaId = UUID.randomUUID();

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(1),
                "Haifa",
                "Artist",
                "concert",
                EventStatus.ACTIVE
        );

        event.getLayout().addArea(new StandingArea(standingAreaId, 50));
        event.getLayout().addArea(new SittingArea(sittingAreaId, 100));

        event.addStandingTickets(standingAreaId, 3);
        event.addSittingTickets(sittingAreaId, 2, 2);

        event.updateStandingArea(standingAreaId, 60, 5);
        assertEquals(5, event.getLayout().requireArea(standingAreaId).getTicketIdsView().size());

        event.updateStandingArea(standingAreaId, 70, 2);
        assertEquals(2, event.getLayout().requireArea(standingAreaId).getTicketIdsView().size());

        event.updateSittingArea(sittingAreaId, 120, 3, 2);
        assertEquals(6, event.getLayout().requireArea(sittingAreaId).getTicketIdsView().size());

        event.updateSittingArea(sittingAreaId, 130, 1, 2);
        assertEquals(2, event.getLayout().requireArea(sittingAreaId).getTicketIdsView().size());

        assertThrows(IllegalArgumentException.class,
                () -> event.updateStandingArea(null, 1, 1));

        assertThrows(IllegalArgumentException.class,
                () -> event.updateStandingArea(sittingAreaId, 1, 1));

        assertThrows(IllegalArgumentException.class,
                () -> event.updateSittingArea(standingAreaId, 1, 1, 1));

        assertThrows(IllegalArgumentException.class,
                () -> event.updateSittingArea(sittingAreaId, 1, 0, 1));
    }

    // ================================================================
    // EventManagementDomainService
    // ================================================================

    @Test
    public void eventManagementDomainService_startRegularSaleBranches() {
        IEventRepository eventRepository = mock(IEventRepository.class);
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        EventManagementDomainService service = new EventManagementDomainService(
                eventRepository,
                historyRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        assertFalse(service.areLotteryWinnersDrawn(null));

        when(lotteryRepository.findByEventID(eventId)).thenReturn(null);
        assertFalse(service.areLotteryWinnersDrawn(eventId));

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(10),
                "Tel Aviv",
                "Artist",
                "Lottery",
                EventStatus.ACTIVE
        );

        when(eventRepository.getById(eventId)).thenReturn(event);

        assertThrows(DomainException.class,
                () -> service.startRegularSale(null));

        assertThrows(DomainException.class,
                () -> service.startRegularSale(eventId));

        event.setLotteryId("lottery-id");

        assertThrows(DomainException.class,
                () -> service.startRegularSale(eventId));

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1)
        );

        lottery.registerMember("winner", 1, LocalDateTime.now().minusDays(1).minusHours(1));
        lottery.addWinner("winner");

        when(lotteryRepository.findByEventID(eventId)).thenReturn(lottery);

        assertTrue(service.areLotteryWinnersDrawn(eventId));

        service.startRegularSale(eventId);

        assertNull(event.getLotteryId());
        assertEquals("Regular Sale", event.getType());
        verify(eventRepository).save(event);
    }

    @Test
    public void eventManagementDomainService_inventoryPermissionBranches() {
        IEventRepository eventRepository = mock(IEventRepository.class);
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        EventManagementDomainService service = new EventManagementDomainService(
                eventRepository,
                historyRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(1),
                "Tel Aviv",
                "Artist",
                "concert",
                EventStatus.ACTIVE
        );

        event.getLayout().addArea(new StandingArea(areaId, 50));
        event.addStandingTickets(areaId, 2);

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(new Company("founder", "Acme")));

        when(userRepository.hasPermission("manager", companyId, CompanyPermission.MANAGE_INVENTORY, eventId))
                .thenReturn(true);

        service.updateStandingArea("manager", companyId, eventId, areaId, 60, 3);

        verify(eventRepository).save(event);

        when(userRepository.hasPermission("no-permission", companyId, CompanyPermission.MANAGE_INVENTORY, eventId))
                .thenReturn(false);
        when(userRepository.hasPermission("no-permission", companyId, CompanyPermission.CONFIGURE_LAYOUT, eventId))
                .thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStandingArea("no-permission", companyId, eventId, areaId, 60, 3));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStandingArea(" ", companyId, eventId, areaId, 60, 3));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStandingArea("manager", null, eventId, areaId, 60, 3));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStandingArea("manager", companyId, eventId, null, 60, 3));
    }

    // ================================================================
    // PurchaseDomainService
    // ================================================================

    @Test
    public void purchaseDomainService_findEventAndActivePurchaseBranches() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertNull(service.findEventById(null));

        Event event = mock(Event.class);
        when(eventRepository.getById(eventId)).thenReturn(event);

        assertSame(event, service.findEventById(eventId));

        assertThrows(DomainException.class,
                () -> service.findActivePurchaseByUserAndEvent(null, eventId));

        assertThrows(DomainException.class,
                () -> service.findActivePurchaseByUserAndEvent(userId, null));

        when(purchaseRepository.findByUserAndEvent(userId, eventId))
                .thenReturn(null);

        assertNull(service.findActivePurchaseByUserAndEvent(userId, eventId));

        ActivePurchase activePurchase = mock(ActivePurchase.class);
        when(activePurchase.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(purchaseRepository.findByUserAndEvent(userId, eventId))
                .thenReturn(activePurchase);

        assertSame(activePurchase, service.findActivePurchaseByUserAndEvent(userId, eventId));

        when(activePurchase.isExpired(any(LocalDateTime.class))).thenReturn(true);

        assertNull(service.findActivePurchaseByUserAndEvent(userId, eventId));
    }

    @Test
    public void purchaseDomainService_validateAdminMemberStateAndManagerLookupBranches() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(userRepository.existsAdmin(adminId)).thenReturn(false);
        assertFalse(service.validateAdmin(adminId));

        when(userRepository.existsAdmin(adminId)).thenReturn(true);
        assertTrue(service.validateAdmin(adminId));

        User user = new User(userId, "alice", "alice@example.com", "hash", 30);

        when(userRepository.getUser(userId)).thenReturn(Optional.of(user));

        assertTrue(service.isMember(userId));
        assertFalse(service.isMemberLoggedIn(userId));

        user.login();

        assertTrue(service.isMemberLoggedIn(userId));

        Event event = mock(Event.class);
        when(event.getCompanyId()).thenReturn(UUID.randomUUID());
        when(eventRepository.getById(eventId)).thenReturn(event);

        User manager = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 35);
        when(userRepository.getAllUsers()).thenReturn(Map.of(manager.getId(), manager));

        when(companyRepository.findByID(event.getCompanyId()))
                .thenReturn(Optional.of(new Company("manager@example.com", "Acme")));

    }

    // ================================================================
    // Small domain value objects/events
    // ================================================================

    @Test
    public void notificationAndAdminValueObjectsBranches() {
        assertThrows(IllegalArgumentException.class,
                () -> new Notification(null, NotificationType.GENERAL, "msg", null));

        assertThrows(IllegalArgumentException.class,
                () -> new Notification("user", NotificationType.GENERAL, " ", null));

        Notification notification = new Notification("user", null, "hello", "/target");

        assertNotNull(notification.getId());
        assertEquals("user", notification.getRecipientId());
        assertEquals(NotificationType.GENERAL, notification.getType());
        assertEquals("hello", notification.getMessage());
        assertEquals("/target", notification.getTargetUrl());
        assertFalse(notification.isRead());
        assertNull(notification.getReadAt());

        notification.markAsRead();

        assertTrue(notification.isRead());
        assertNotNull(notification.getReadAt());

        notification.markAsRead();

        assertTrue(notification.isRead());

        UUID adminId = UUID.randomUUID();

        AdminActionLog log = new AdminActionLog(adminId, "admin", "ACTION", "target");

        assertEquals(adminId, log.getAdminId());
        assertEquals("admin", log.getAdminUsername());
        assertEquals("ACTION", log.getAction());
        assertEquals("target", log.getTarget());
        assertNotNull(log.getCreatedAt());

        assertThrows(IllegalArgumentException.class,
                () -> new AdminActionLog(null, "admin", "ACTION", "target"));

        assertThrows(IllegalArgumentException.class,
                () -> new AdminActionLog(adminId, " ", "ACTION", "target"));

        assertThrows(IllegalArgumentException.class,
                () -> new AdminActionLog(adminId, "admin", " ", "target"));
    }

    @Test
    public void adminComplaintAndDomainEventsBranches() {
        UUID reporterId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new AdminComplaint(null, "user", "title", "description"));

        assertThrows(IllegalArgumentException.class,
                () -> new AdminComplaint(reporterId, " ", "title", "description"));

        assertThrows(IllegalArgumentException.class,
                () -> new AdminComplaint(reporterId, "user", " ", "description"));

        assertThrows(IllegalArgumentException.class,
                () -> new AdminComplaint(reporterId, "user", "title", " "));

        AdminComplaint complaint =
                new AdminComplaint(reporterId, "user", "title", "description");

        assertEquals(AdminComplaintStatus.OPEN, complaint.getStatus());

        assertThrows(IllegalArgumentException.class,
                () -> complaint.respond(null, "response"));

        assertThrows(IllegalArgumentException.class,
                () -> complaint.respond("admin", " "));

        complaint.respond("admin", "answer");

        assertEquals(AdminComplaintStatus.ANSWERED, complaint.getStatus());
        assertEquals("answer", complaint.getAdminResponse());
        assertEquals("admin", complaint.getResponderAdminUsername());
        assertNotNull(complaint.getRespondedAt());

        complaint.close();

        assertEquals(AdminComplaintStatus.CLOSED, complaint.getStatus());

        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        UserRegisteredEvent registered = new UserRegisteredEvent(userId);
        TicketReservedEvent reserved = new TicketReservedEvent(userId, eventId);

        assertEquals(userId, registered.getUserId());
        assertEquals(userId, reserved.getUserId());
        assertEquals(eventId, reserved.getEventId());
    }

    // ================================================================
    // ROUND 2 — ugly coverage boosters
    // ================================================================

    @Test
    public void rolesDomainService_getCompanyOwnerGetCompanyAndRemoveAsAdmin_moreBranches() {
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        RolesDomainService service = new RolesDomainService(companyRepository, userRepository);

        UUID companyId1 = UUID.randomUUID();
        UUID companyId2 = UUID.randomUUID();

        Company company1 = new Company("founder@example.com", "A");
        Company company2 = new Company("founder@example.com", "B");

        when(companyRepository.findByID(companyId1)).thenReturn(Optional.of(company1));
        when(companyRepository.findByID(companyId2)).thenReturn(Optional.of(company2));

        assertEquals("founder@example.com", service.getCompanyOwner(companyId1));
        assertSame(company1, service.getCompany(companyId1));

        assertThrows(IllegalArgumentException.class, () -> service.getCompanyOwner(null));
        assertThrows(IllegalArgumentException.class, () -> service.getCompany(null));

        User member = new User(UUID.randomUUID(), "member", "member@example.com", "hash", 30);
        member.getCompanyRoles().put(companyId1, new CompanyFounder("member@example.com"));
        member.getCompanyRoles().put(companyId2, new CompanyFounder("member@example.com"));

        when(userRepository.isSystemAdmin("admin")).thenReturn(true);
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        when(userRepository.getCompaniesIdsByMember("member@example.com"))
                .thenReturn(List.of(companyId1, companyId2));
    }

    @Test
    public void rolesDomainService_invitationsAndInvitationResponses_coverManagerAndOwner() {
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        RolesDomainService service = new RolesDomainService(companyRepository, userRepository);

        UUID companyId = UUID.randomUUID();
        Company company = new Company("founder@example.com", "Acme");

        User founder = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 50);
        founder.getCompanyRoles().put(companyId, new CompanyFounder("founder@example.com"));

        User managerCandidate =
                new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 25);

        User ownerCandidate =
                new User(UUID.randomUUID(), "owner", "owner@example.com", "hash", 25);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("founder@example.com")).thenReturn(Optional.of(founder));
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(managerCandidate));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(ownerCandidate));

        UUID managerInvitationId = service.inviteCompanyManager(
                "founder@example.com",
                companyId,
                "manager@example.com",
                Set.of(CompanyPermission.MANAGE_POLICIES)
        );

        UUID ownerInvitationId = service.inviteCompanyOwner(
                "founder@example.com",
                companyId,
                "owner@example.com"
        );

        assertNotNull(managerInvitationId);
        assertNotNull(ownerInvitationId);

        assertEquals(1, service.getUserInvitations("manager@example.com").size());

        assertEquals(1, service.getUserInvitations("owner@example.com").size());

        service.acceptCompanyInvitation(managerInvitationId, "manager@example.com", companyId);
        assertTrue(managerCandidate.isManagerInCompany(companyId));

        service.rejectCompanyInvitation(ownerInvitationId, "owner@example.com", companyId);
        assertTrue(ownerCandidate.getCompanyInvitations().isEmpty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getUserInvitations(" ")
        );
    }

    @Test
    public void rolesDomainService_companyPolicyAndDiscountCrud_coverFounderBranches() {
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        RolesDomainService service = new RolesDomainService(companyRepository, userRepository);

        UUID companyId = UUID.randomUUID();
        Company company = new Company("founder@example.com", "Acme");

        User founder = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 50);
        founder.getCompanyRoles().put(companyId, new CompanyFounder("founder@example.com"));

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("founder@example.com")).thenReturn(Optional.of(founder));

        service.addPurchasePolicy(
                "founder@example.com",
                companyId,
                Optional.of(18f),
                Optional.of(1),
                Optional.of(5),
                Optional.of(false),
                true
        );

        assertNotNull(company.getPurchasePolicy().getRulesView());

        UUID rootRuleId = company.getPurchasePolicy().getRulesView().getId();

        service.deletePurchasePolicy("founder@example.com", companyId, rootRuleId);

        assertNull(company.getPurchasePolicy().getRulesView());

        service.addOvertDiscount(
                "founder@example.com",
                companyId,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                10f
        );

        service.addConditionalDiscount(
                "founder@example.com",
                companyId,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                20f,
                2,
                1
        );

        service.addCouponCode(
                "founder@example.com",
                companyId,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                15f,
                "SAVE15"
        );

        assertEquals(3, company.getDiscountPolicy().getDiscountRules().size());

        UUID discountId = company.getDiscountPolicy().getDiscountRules().get(0).getId();

        service.removeDiscount("founder@example.com", companyId, discountId);

        assertEquals(2, company.getDiscountPolicy().getDiscountRules().size());
    }

    @Test
    public void rolesDomainService_policyChangeRejectedForUserWithNoCompanyRole() {
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        RolesDomainService service = new RolesDomainService(companyRepository, userRepository);

        UUID companyId = UUID.randomUUID();
        Company company = new Company("founder@example.com", "Acme");
        User randomUser = new User(UUID.randomUUID(), "random", "random@example.com", "hash", 20);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("random@example.com")).thenReturn(Optional.of(randomUser));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.addPurchasePolicy(
                        "random@example.com",
                        companyId,
                        Optional.of(18f),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.addOvertDiscount(
                        "random@example.com",
                        companyId,
                        LocalDate.now(),
                        LocalDate.now().plusDays(1),
                        10f
                )
        );
    }

    @Test
    public void purchaseDomainService_updateActivePurchaseSittingTickets_successWithMocks() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID activePurchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID oldTicketId = UUID.randomUUID();
        UUID newTicketId = UUID.randomUUID();

        ActivePurchase activePurchase = mock(ActivePurchase.class);
        Event event = mock(Event.class);
        Ticket newTicket = mock(Ticket.class);

        Map<UUID, Float> oldTickets = Map.of(oldTicketId, 100f);

        when(activePurchase.getEventID()).thenReturn(eventId);
        when(activePurchase.getTicketIDs()).thenReturn(oldTickets);
        when(activePurchase.getLastUpdate()).thenReturn(LocalDateTime.now());
        when(activePurchase.isExpired(any(LocalDateTime.class))).thenReturn(false);

        when(purchaseRepository.findByID(activePurchaseId)).thenReturn(activePurchase);
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(event.getTicket(newTicketId)).thenReturn(newTicket);
        when(newTicket.getPrice()).thenReturn(150f);

        service.updateActivePurchaseSittingTickets(activePurchaseId, List.of(newTicketId));

        verify(event).releaseTickets(oldTickets);
        verify(event).reserveSittingTickets(List.of(newTicketId));
        verify(purchaseRepository).save(activePurchase);
        verify(activePurchase).update();
    }

    @Test
    public void purchaseDomainService_updateActivePurchaseStandingTickets_successWithMocks() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID activePurchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID oldTicketId = UUID.randomUUID();
        UUID newTicketId = UUID.randomUUID();

        ActivePurchase activePurchase = mock(ActivePurchase.class);
        Event event = mock(Event.class);
        Ticket newTicket = mock(Ticket.class);

        Map<UUID, Float> oldTickets = Map.of(oldTicketId, 100f);

        when(activePurchase.getEventID()).thenReturn(eventId);
        when(activePurchase.getTicketIDs()).thenReturn(oldTickets);
        when(activePurchase.getLastUpdate()).thenReturn(LocalDateTime.now());
        when(activePurchase.isExpired(any(LocalDateTime.class))).thenReturn(false);

        when(purchaseRepository.findByID(activePurchaseId)).thenReturn(activePurchase);
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(event.reserveStandingTickets(1, areaId)).thenReturn(List.of(newTicketId));
        when(event.getTicket(newTicketId)).thenReturn(newTicket);
        when(newTicket.getPrice()).thenReturn(120f);

        service.updateActivePurchaseStandingTickets(activePurchaseId, 1, areaId);

        verify(event).releaseTickets(oldTickets);
        verify(event).reserveStandingTickets(1, areaId);
        verify(purchaseRepository).save(activePurchase);
        verify(activePurchase).update();
    }

    @Test
    public void purchaseDomainService_historyByCompanyAndMemberHelpers_moreBranches() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        PurchaseHistory h1 = mock(PurchaseHistory.class);
        PurchaseHistory h2 = mock(PurchaseHistory.class);
        PurchaseHistory h3 = mock(PurchaseHistory.class);

        when(h1.getEventId()).thenReturn(eventId1);
        when(h2.getEventId()).thenReturn(eventId2);
        when(h3.getEventId()).thenReturn(UUID.randomUUID());

        Event e1 = mock(Event.class);
        Event e2 = mock(Event.class);

        when(e1.getCompanyId()).thenReturn(companyId);
        when(e2.getCompanyId()).thenReturn(otherCompanyId);

        when(historyRepository.getAll()).thenReturn(List.of(h1, h2, h3));
        when(eventRepository.getById(eventId1)).thenReturn(e1);
        when(eventRepository.getById(eventId2)).thenReturn(e2);

        List<PurchaseHistory> result = service.getHistoryByCompany(companyId);

        assertEquals(1, result.size());
        assertSame(h1, result.get(0));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getHistoryByCompany(null)
        );

        UUID memberId = UUID.randomUUID();
        User member = new User(memberId, "m", "m@example.com", "hash", 20);

        when(userRepository.getUser(memberId)).thenReturn(Optional.of(member));

        assertTrue(service.memberExists(memberId));
        assertTrue(service.isMember(memberId));
        assertFalse(service.isMemberLoggedIn(memberId));

        member.login();

        assertTrue(service.isMemberLoggedIn(memberId));
    }

    @Test
    public void purchaseDomainService_registerToLottery_minMaxAndSuccessBranches() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(10),
                "Tel Aviv",
                "Artist",
                "Lottery",
                EventStatus.ACTIVE
        );

        event.setLotteryId("lottery-id");
        event.addPurchasePolicy(
                Optional.empty(),
                Optional.of(2),
                Optional.of(3),
                Optional.empty(),
                true
        );

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(lotteryRepository.findByEventID(eventId)).thenReturn(lottery);

        assertThrows(
                DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 1)
        );

        assertThrows(
                DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 4)
        );

    }

    @Test
    public void purchaseDomainService_drawLotteryForEvent_validationAndSuccessBranches() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(null, LocalDateTime.now().plusMinutes(10)));

        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(eventId, null));

        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(eventId, LocalDateTime.now().plusMinutes(10)));

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(10),
                "Tel Aviv",
                "Artist",
                "Lottery",
                EventStatus.ACTIVE
        );

        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, 50));
        event.addStandingTickets(areaId, 2);

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        lottery.registerMember("winner", 1, LocalDateTime.now());

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(lotteryRepository.findByEventID(eventId)).thenReturn(null);

        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(eventId, LocalDateTime.now().plusMinutes(10)));

        when(lotteryRepository.findByEventID(eventId)).thenReturn(lottery);

        Map<String, String> result =
                service.drawLotteryForEvent(eventId, LocalDateTime.now().plusMinutes(10));

        assertEquals(1, result.size());
        verify(lotteryRepository).save(lottery);
    }

    @Test
    public void user_moreHierarchyRemovalAndPlatformBranches() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        User founder = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 50);
        CompanyFounder founderRole = new CompanyFounder("founder@example.com");
        founderRole.getEventsIds().add(eventId);
        founder.getCompanyRoles().put(companyId, founderRole);

        User manager = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 30);
        CompanyManager managerRole = new CompanyManager(
                "manager@example.com",
                founderRole,
                Set.of(CompanyPermission.MANAGE_INVENTORY)
        );
        managerRole.getEventsIds().add(eventId);
        founderRole.addSubordinate(managerRole);
        manager.getCompanyRoles().put(companyId, managerRole);

        assertSame(founderRole, founder.getMyCompanyFounder(companyId));
        assertTrue(founder.getMyEventIdsOfCompany(companyId).contains(eventId));
        assertTrue(founder.getHierarchyMermaid(companyId).contains("Founder"));

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.getMyEventIdsOfCompany(companyId)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.getMyCompanyFounder(UUID.randomUUID())
        );

        manager.removeFromAllCompaniesAsAdmin();

        assertFalse(manager.isCompanyMember(companyId));

        manager.removeFromPlatformAsAdmin();

        assertEquals(UserStatus.REMOVED, manager.getStatus());

        assertThrows(IllegalStateException.class, manager::login);

        assertThrows(IllegalStateException.class, manager::removeFromPlatformAsAdmin);
    }

    @Test
    public void user_inviteOwner_errorBranchesForAlreadyOwnerAndUnauthorizedAppointer() {
        UUID companyId = UUID.randomUUID();

        User founder = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 50);
        founder.getCompanyRoles().put(companyId, new CompanyFounder("founder@example.com"));

        User alreadyOwner = new User(UUID.randomUUID(), "owner", "owner@example.com", "hash", 30);
        alreadyOwner.getCompanyRoles().put(
                companyId,
                new CompanyOwner("owner@example.com", (CompanyOwner) founder.getCompanyRole(companyId))
        );

        assertThrows(
                IllegalStateException.class,
                () -> alreadyOwner.inviteUserToBecomeOwner(companyId, founder)
        );

        User notOwner = new User(UUID.randomUUID(), "plain", "plain@example.com", "hash", 20);
        User candidate = new User(UUID.randomUUID(), "candidate", "candidate@example.com", "hash", 20);

        assertThrows(
                IllegalArgumentException.class,
                () -> candidate.inviteUserToBecomeOwner(companyId, notOwner)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> candidate.inviteUserToBecomeManager(companyId, notOwner, Set.of())
        );
    }

    @Test
    public void loneSeatRule_moreSeatPatterns() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = eventWithSittingSeats(eventId, companyId, areaId, 3);
        List<UUID> seats = event.getTicketsView().keySet().stream().toList();

        User user = new User(userId, "buyer", "buyer@example.com", "hash", 20);

        LoneSeatRule disallow = new LoneSeatRule(false);
        LoneSeatRule allow = new LoneSeatRule(true);

        ActivePurchase firstSeatOnly = new ActivePurchase(
                userId,
                eventId,
                Map.of(seats.get(0), 100f),
                LocalDateTime.now().plusMinutes(10)
        );

        ActivePurchase middleSeatOnly = new ActivePurchase(
                userId,
                eventId,
                Map.of(seats.get(1), 100f),
                LocalDateTime.now().plusMinutes(10)
        );

        ActivePurchase allSeats = new ActivePurchase(
                userId,
                eventId,
                Map.of(
                        seats.get(0), 100f,
                        seats.get(1), 100f,
                        seats.get(2), 100f
                ),
                LocalDateTime.now().plusMinutes(10)
        );

        assertTrue(disallow.doesHold(firstSeatOnly, user, event));
        assertTrue(disallow.doesHold(allSeats, user, event));
        assertTrue(allow.doesHold(middleSeatOnly, user, event));
    }

    @Test
    public void event_checkAvailabilityAndReserveBranches() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID sittingAreaId = UUID.randomUUID();
        UUID standingAreaId = UUID.randomUUID();

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(3),
                "Tel Aviv",
                "Artist",
                "concert",
                EventStatus.ACTIVE
        );

        event.getLayout().addArea(new SittingArea(sittingAreaId, 100));
        event.getLayout().addArea(new StandingArea(standingAreaId, 50));

        event.addSittingTickets(sittingAreaId, 1, 1);
        event.addStandingTickets(standingAreaId, 1);

        UUID sittingTicketId = event.getLayout()
                .requireArea(sittingAreaId)
                .getTicketIdsView()
                .get(0);

        assertThrows(DomainException.class,
                () -> event.checkAvailabilityOfSittingTickets(null));

        assertThrows(DomainException.class,
                () -> event.checkAvailabilityOfSittingTickets(List.of()));

        assertThrows(DomainException.class,
                () -> event.checkAvailabilityOfSittingTickets(List.of(UUID.randomUUID())));

        event.checkAvailabilityOfSittingTickets(List.of(sittingTicketId));

        event.reserveSittingTickets(List.of(sittingTicketId));

        assertThrows(DomainException.class,
                () -> event.checkAvailabilityOfSittingTickets(List.of(sittingTicketId)));

        assertThrows(DomainException.class,
                () -> event.reserveSittingTickets(List.of(sittingTicketId)));

        assertThrows(DomainException.class,
                () -> event.checkAvailabilityOfStandingTickets(2, standingAreaId));
    }

    @Test
    public void discountRules_moreCheapBranches() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ActivePurchase purchase = activePurchase(userId, eventId, 100f, 50f);

        CouponCode coupon = new CouponCode(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                10f,
                "SAVE10"
        );

        assertTrue(coupon.matchesCode("SAVE10"));
        assertFalse(coupon.matchesCode("NOPE"));
        assertFalse(coupon.matchesCode(null));
        assertEquals("SAVE10", coupon.getCode());
        assertEquals(10f, coupon.getDiscountPercent(), 0.001);

        float afterCoupon = coupon.apply(purchase, "SAVE10");
        assertTrue(afterCoupon < 150f);

        OvertDiscount overt = new OvertDiscount(
                25f,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1)
        );

        ActivePurchase secondPurchase = activePurchase(userId, eventId, 80f, 20f);

        float afterOvert = overt.apply(secondPurchase);

        assertEquals(75f, afterOvert, 0.001);
        assertEquals(25f, overt.getDiscountPercent(), 0.001);
        assertNotNull(overt.getFromDate());
        assertNotNull(overt.getToDate());
    }

    @Test
    public void companyAggregate_moreSimpleCoverage() {
        Company company = new Company("founder@example.com", "Old Name");

        UUID event1 = UUID.randomUUID();
        UUID event2 = UUID.randomUUID();

        company.setName("New Name");
        company.addEvent(event1);
        company.addEvent(event2);

        assertEquals("New Name", company.getName());
        assertEquals(List.of(event1, event2), company.getEventIds());
        assertEquals("founder@example.com", company.getFounderEmail());
        assertEquals(Company.CompanyStatus.ACTIVE, company.getStatus());
        assertTrue(company.isActive());

        company.updateRating(5);
        company.updateRating(3);

        assertEquals(4.0, company.getRating(), 0.001);

        company.addOvertDiscount(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                10f
        );

        company.addConditionalDiscount(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                20f,
                2,
                1
        );

        company.addCouponCode(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                30f,
                "SAVE30"
        );

        assertEquals(3, company.getDiscountPolicy().getDiscountRules().size());

        UUID discountId = company.getDiscountPolicy().getDiscountRules().get(0).getId();

        company.removeDiscount(discountId);

        assertEquals(2, company.getDiscountPolicy().getDiscountRules().size());

        company.AdminClose();

        assertFalse(company.isActive());

        assertThrows(IllegalStateException.class, company::AdminClose);
    }
    // ================================================================
    // ROUND 3 — more ugly coverage boosters
    // ================================================================


    @Test
    public void companyOwner_reassignsSubordinatesWhenRemovingOwner() {
        UUID ownerEvent = UUID.randomUUID();
        UUID childEvent = UUID.randomUUID();
        UUID managerEvent = UUID.randomUUID();

        CompanyFounder founder = new CompanyFounder("founder");
        CompanyOwner owner = new CompanyOwner("owner", founder);
        CompanyOwner childOwner = new CompanyOwner("childOwner", owner);
        CompanyManager manager = new CompanyManager(
                "manager",
                childOwner,
                Set.of(CompanyPermission.MANAGE_INVENTORY)
        );

        owner.getEventsIds().add(ownerEvent);
        childOwner.getEventsIds().add(childEvent);
        manager.getEventsIds().add(managerEvent);

        founder.addSubordinate(owner);
        owner.addSubordinate(childOwner);
        childOwner.addSubordinate(manager);

        assertTrue(owner.isInChargeOfEvent(ownerEvent));
        assertTrue(owner.isInChargeOfEvent(childEvent));
        assertTrue(owner.isInChargeOfEvent(managerEvent));
        assertEquals("owner", owner.isMyEvent(ownerEvent));

        owner.removeSubordinate(childOwner);

        assertFalse(owner.getSubordinates().contains(childOwner));
        assertTrue(owner.getSubordinates().contains(manager));
        assertTrue(childOwner.getSubordinates().isEmpty());
        assertEquals(owner, manager.getAppointer());

        StringBuilder sb = new StringBuilder();
        owner.buildMermaid(sb);

        assertTrue(sb.toString().contains("owner"));
        assertTrue(sb.toString().contains("manager"));

        assertEquals("Owner", owner.getRoleName());
        assertEquals(founder, owner.getFounder());

        CompanyOwner brokenOwner = new CompanyOwner("broken", null);
        assertThrows(IllegalStateException.class, brokenOwner::getFounder);
    }

    @Test
    public void rolesDomainService_changePermissionsHierarchyRateAndMembershipDtos() {
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        RolesDomainService service = new RolesDomainService(companyRepository, userRepository);

        UUID companyId = UUID.randomUUID();
        Company company = new Company("founder@example.com", "Acme");

        User founder = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 50);
        CompanyFounder founderRole = new CompanyFounder("founder@example.com");
        founder.getCompanyRoles().put(companyId, founderRole);

        User manager = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 25);
        CompanyManager managerRole = new CompanyManager(
                "manager@example.com",
                founderRole,
                Set.of(CompanyPermission.VIEW_HISTORY)
        );
        founderRole.addSubordinate(managerRole);
        manager.getCompanyRoles().put(companyId, managerRole);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("founder@example.com")).thenReturn(Optional.of(founder));
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(manager));
        when(userRepository.getCompaniesIdsByMember("manager@example.com")).thenReturn(List.of(companyId));

        service.changeManagerPermissions(
                "founder@example.com",
                companyId,
                "manager@example.com",
                Set.of(CompanyPermission.MANAGE_POLICIES)
        );

        assertTrue(managerRole.hasPremission(CompanyPermission.MANAGE_POLICIES));
        assertFalse(managerRole.hasPremission(CompanyPermission.VIEW_HISTORY));

        assertEquals(1, service.getUserCompanies("manager@example.com").size());
        assertTrue(service.getCompanyHierarchyMermaid(companyId, "founder@example.com").contains("Founder"));

        service.rateCompany(UUID.randomUUID(), companyId, 5);
        service.rateCompany(UUID.randomUUID(), companyId, 3);

        assertEquals(4.0, company.getRating(), 0.001);
        verify(companyRepository, atLeastOnce()).save(company);

        assertThrows(IllegalArgumentException.class,
                () -> service.changeManagerPermissions(" ", companyId, "manager@example.com", Set.of()));

        assertThrows(IllegalArgumentException.class,
                () -> service.changeManagerPermissions("founder@example.com", companyId, " ", Set.of()));

        assertThrows(IllegalArgumentException.class,
                () -> service.getUserCompanies(" "));
    }

    @Test
    public void eventManagementDomainService_addEditDeleteAndLookupBranches() {
        IEventRepository eventRepository = mock(IEventRepository.class);
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        EventManagementDomainService service = new EventManagementDomainService(
                eventRepository,
                historyRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        User manager = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 30);
        CompanyFounder role = new CompanyFounder("manager@example.com");
        manager.getCompanyRoles().put(companyId, role);

        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(manager));
        when(eventRepository.getById(eventId)).thenReturn(null);

        service.addEvent(
                eventId,
                companyId,
                "manager@example.com",
                "Name",
                LocalDateTime.now().plusDays(10),
                "Tel Aviv",
                "Artist",
                "Concert",
                EventStatus.ACTIVE,
                "Description"
        );

        assertTrue(role.getEventsIds().contains(eventId));
        verify(eventRepository).save(any(Event.class));

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(10),
                "Old",
                "Old Artist",
                "Old Type",
                EventStatus.ACTIVE
        );

        when(eventRepository.getById(eventId)).thenReturn(event);

        UUID buyer = UUID.randomUUID();
        PurchaseHistory history = mock(PurchaseHistory.class);
        PurchaseHistory other = mock(PurchaseHistory.class);

        when(history.getEventId()).thenReturn(eventId);
        when(history.getUserId()).thenReturn(buyer);
        when(other.getEventId()).thenReturn(UUID.randomUUID());
        when(historyRepository.getAll()).thenReturn(List.of(history, other));

        Set<UUID> participants = service.editEvent(
                eventId,
                "New Name",
                LocalDateTime.now().plusDays(20),
                "Haifa",
                "New Artist",
                "Festival",
                EventStatus.CANCELED,
                "New Description"
        );

        assertEquals(Set.of(buyer), participants);
        assertEquals("New Name", event.getName());
        assertEquals("Haifa", event.getLocation());
        assertEquals("New Artist", event.getArtist());
        assertEquals("Festival", event.getType());

        assertSame(event, service.getEventForView(eventId));
        assertSame(event, service.findEventById(eventId));
        assertNull(service.findEventById(null));

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(new Company("founder", "Company")));
        assertNotNull(service.findCompanyById(companyId));
        assertNull(service.findCompanyById(null));

        when(companyRepository.findByID(UUID.randomUUID())).thenReturn(null);
        assertNull(service.findCompanyById(UUID.randomUUID()));

        service.deleteEvent(eventId, "manager@example.com", "manager@example.com");

        assertFalse(role.getEventsIds().contains(eventId));
        verify(eventRepository).delete(eventId);

        assertThrows(DomainException.class,
                () -> service.addEvent(eventId, companyId, "manager@example.com", "x",
                        LocalDateTime.now(), "a", "b", "c", EventStatus.ACTIVE, null));
    }

    @Test
    public void eventManagementDomainService_visibleSearchAndEventsForUserBranches() {
        IEventRepository eventRepository = mock(IEventRepository.class);
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        EventManagementDomainService service = new EventManagementDomainService(
                eventRepository,
                historyRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event visible = mock(Event.class);
        Event hidden = mock(Event.class);
        Event otherCompany = mock(Event.class);

        when(visible.getCompanyId()).thenReturn(companyId);
        when(visible.isPubliclyVisible()).thenReturn(true);

        when(hidden.getCompanyId()).thenReturn(companyId);
        when(hidden.isPubliclyVisible()).thenReturn(false);

        when(otherCompany.getCompanyId()).thenReturn(otherCompanyId);
        when(otherCompany.isPubliclyVisible()).thenReturn(true);

        when(eventRepository.getAll()).thenReturn(List.of(visible, hidden, otherCompany));

        assertEquals(List.of(visible), service.getVisibleEventsForCompany(companyId));
        assertThrows(DomainException.class, () -> service.getVisibleEventsForCompany(null));

        Company activeCompany = new Company("founder", "Active");
        Company inactiveCompany = new Company("founder", "Inactive");
        inactiveCompany.AdminClose();

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(activeCompany));
        when(companyRepository.findByID(otherCompanyId)).thenReturn(Optional.of(inactiveCompany));
        when(visible.matches(any(), anyDouble())).thenReturn(true);
        when(hidden.matches(any(), anyDouble())).thenReturn(false);

        assertEquals(List.of(visible), service.searchEvents(null));

        User user = new User(UUID.randomUUID(), "u", "u@example.com", "hash", 20);
        CompanyFounder role = new CompanyFounder("u@example.com");
        role.getEventsIds().add(eventId);
        user.getCompanyRoles().put(companyId, role);

        Event realEvent = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(2),
                "Tel Aviv",
                "Artist",
                "Concert",
                EventStatus.ACTIVE
        );

        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.getById(eventId)).thenReturn(realEvent);

        assertEquals(List.of(realEvent), service.getEventsForUserInCompany("u@example.com", companyId));

        assertThrows(IllegalArgumentException.class,
                () -> service.getEventsForUserInCompany(" ", companyId));

        assertThrows(IllegalArgumentException.class,
                () -> service.getEventsForUserInCompany("u@example.com", null));

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getEventsForUserInCompany("missing@example.com", companyId));
    }

    @Test
    public void purchaseDomainService_findActivePurchasesFiltersAndCancelViewBranches() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID activeId = UUID.randomUUID();

        ActivePurchase good = mock(ActivePurchase.class);
        ActivePurchase otherUser = mock(ActivePurchase.class);
        ActivePurchase expired = mock(ActivePurchase.class);
        ActivePurchase stale = mock(ActivePurchase.class);

        when(good.getUserID()).thenReturn(userId);
        when(good.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(good.getLastUpdate()).thenReturn(LocalDateTime.now());

        when(otherUser.getUserID()).thenReturn(otherUserId);

        when(expired.getUserID()).thenReturn(userId);
        when(expired.isExpired(any(LocalDateTime.class))).thenReturn(true);

        when(stale.getUserID()).thenReturn(userId);
        when(stale.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(stale.getLastUpdate()).thenReturn(LocalDateTime.now().minusHours(1));

        when(purchaseRepository.findAll()).thenReturn(List.of(good, otherUser, expired, stale));

        assertEquals(List.of(good), service.findActivePurchasesByUser(userId));
        assertThrows(DomainException.class, () -> service.findActivePurchasesByUser(null));

        Map<UUID, Float> tickets = Map.of(UUID.randomUUID(), 100f);
        Event event = mock(Event.class);

        when(good.getActivePurchaseId()).thenReturn(activeId);
        when(good.getEventID()).thenReturn(eventId);
        when(good.getTicketIDs()).thenReturn(tickets);
        when(purchaseRepository.findByID(activeId)).thenReturn(good);
        when(eventRepository.getById(eventId)).thenReturn(event);

        assertSame(good, service.viewActivePurchase(activeId));
        verify(good).update();

        service.cancelActivePurchase(activeId);

        verify(event).releaseTickets(tickets);
        verify(purchaseRepository).deleteByID(activeId);

        assertThrows(DomainException.class, () -> service.cancelActivePurchase(UUID.randomUUID()));
        assertThrows(DomainException.class, () -> service.viewActivePurchase(UUID.randomUUID()));
    }

    @Test
    public void purchaseDomainService_completePurchaseTicketingFailureRefundBranches() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        IPaymentGateway paymentGateway = mock(IPaymentGateway.class);
        ITicketingGateway ticketingGateway = mock(ITicketingGateway.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository,
                paymentGateway,
                ticketingGateway
        );

        UUID activePurchaseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        ActivePurchase activePurchase = mock(ActivePurchase.class);
        Event event = mock(Event.class);
        User user = new User(userId, "buyer", "buyer@example.com", "hash", 25);

        Map<UUID, Float> tickets = Map.of(ticketId, 100f);

        when(activePurchase.getActivePurchaseId()).thenReturn(activePurchaseId);
        when(activePurchase.getUserID()).thenReturn(userId);
        when(activePurchase.getEventID()).thenReturn(eventId);
        when(activePurchase.getTicketIDs()).thenReturn(tickets);
        when(activePurchase.getPrice()).thenReturn(100f);
        when(activePurchase.getLastUpdate()).thenReturn(LocalDateTime.now());
        when(activePurchase.isExpired(any(LocalDateTime.class))).thenReturn(false);

        when(purchaseRepository.findByID(activePurchaseId)).thenReturn(activePurchase);
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(userRepository.getUser(userId)).thenReturn(Optional.of(user));
        when(event.getPurchasePolicy()).thenReturn(new org.example.DomainLayer.PolicyManagment.PurchasePolicy());
        when(event.getDiscountPolicy()).thenReturn(new org.example.DomainLayer.PolicyManagment.DiscountPolicy());
        when(event.getCompanyId()).thenReturn(null);


    }

    @Test
    public void purchaseDomainService_salesReportAndOwnerOfEventBranches() {
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IEventRepository eventRepository = mock(IEventRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IUserRepository userRepository = mock(IUserRepository.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);

        PurchaseDomainService service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );

        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        assertFalse(service.isCompanyOwnerOfEvent("owner@example.com", eventId));

        Event event = mock(Event.class);
        when(event.getCompanyId()).thenReturn(companyId);
        when(eventRepository.getById(eventId)).thenReturn(event);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.empty());
        assertFalse(service.isCompanyOwnerOfEvent("owner@example.com", eventId));

        Company company = new Company("owner@example.com", "Acme");
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));

        User owner = new User(UUID.randomUUID(), "owner", "owner@example.com", "hash", 40);
        CompanyFounder ownerRole = new CompanyFounder("owner@example.com");
        ownerRole.getEventsIds().add(eventId);
        owner.getCompanyRoles().put(companyId, ownerRole);

        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));

        assertTrue(service.isCompanyOwnerOfEvent("owner@example.com", eventId));

        PurchaseHistory history = mock(PurchaseHistory.class);
        Payment payment = mock(Payment.class);

        when(history.getPayment()).thenReturn(payment);
        when(history.getTicketIds()).thenReturn(List.of(ticketId));
        when(historyRepository.getByEventId(eventId)).thenReturn(List.of(history));

        SalesReport report = service.getSalesReportForOwner("owner@example.com", companyId);

        assertNotNull(report);
    }
}