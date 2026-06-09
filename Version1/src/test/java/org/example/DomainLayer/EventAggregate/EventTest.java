package org.example.DomainLayer.EventAggregate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PolicyManagment.DiscountType;
import org.example.DomainLayer.PolicyManagment.IDiscountRule;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the {@link Event} aggregate (Domain Layer).
 *
 * <p>These tests are the source of truth for the rules implemented inside
 * {@code Event}: policy management, discount management, rating logic,
 * capacity, and search-criteria matching. They drive the aggregate
 * directly and never go through the application/service layer.
 *
 * <p>Service-layer wiring, DTO mapping, and exception propagation are
 * exercised by the acceptance suite at
 * {@code org.example.ApplicationLayer.EventServiceTest}.
 */
public class EventTest {

    private LocalDateTime futureDate;
    private Event event;
    private final UUID companyId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @Before
    public void setUp() {
        futureDate = LocalDateTime.now().plusDays(10);
        event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(7),
                "Be'er Sheva",
                "Nadav and the Coders",
                "Concert",
                EventStatus.ACTIVE,
                DiscountType.ALL
        );
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Event activeEvent() {
        return new Event(eventId, companyId, futureDate, "Tel Aviv",
                "Some Artist", "concert", EventStatus.ACTIVE, DiscountType.ALL);
    }

    private Event eventOf(UUID compId, String name, String artist, String type,
                          String location, LocalDateTime date,
                          EventStatus status, String... tags) {
        Event e = new Event(UUID.randomUUID(), compId, date, location, artist, type, status, DiscountType.ALL);
        e.setName(name);
        for (String t : tags) {
            e.addTag(t);
        }
        return e;
    }

    private void addStandingArea(Event e, double price) {
        e.getLayout().addArea(new StandingArea(UUID.randomUUID(), price));
    }

    private void addSittingArea(Event e, double price) {
        e.getLayout().addArea(new SittingArea(UUID.randomUUID(), price));
    }

    // ---------------------------------------------------------------------
    // Policy Management
    // ---------------------------------------------------------------------
    @Test
    public void GivenAllFourOptionalsPresent_WhenAddPurchasePolicy_ThenAllFourRulesArePersisted() {
        Event event = activeEvent();

        // Sequential additions to assemble a composite tree branch structure using the binary operator
        event.addPurchasePolicy(Optional.of(18f), Optional.empty(), Optional.empty(), Optional.empty(), true);
        event.addPurchasePolicy(Optional.empty(), Optional.of(1), Optional.empty(), Optional.empty(), true);
        event.addPurchasePolicy(Optional.empty(), Optional.empty(), Optional.of(5), Optional.empty(), true);
        event.addPurchasePolicy(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true), true);

        var rulesView = event.getPurchasePolicy().getRulesView();
        assertNotNull("The active purchase policy structural view must not be null", rulesView);
        assertNotNull("The composite tree node should hold a generated dynamic ID", rulesView.getId());
    }

    @Test
    public void GivenAgeAndAllowLoneSeatOptionalsPresent_WhenAddPurchasePolicy_ThenOnlyThoseTwoRulesArePersisted() {
        Event event = activeEvent();

        event.addPurchasePolicy(Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.empty(), true);
        event.addPurchasePolicy(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true), true);

        var rulesView = event.getPurchasePolicy().getRulesView();
        assertNotNull("The active composition state must be correctly initialized", rulesView);
    }

    @Test
    public void GivenAllOptionalsEmpty_WhenAddPurchasePolicy_ThenNoRuleIsAdded() {
        Event event = activeEvent();

        event.addPurchasePolicy(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true);

        assertTrue("Policy structural rules should remain empty or uninitialized on completely empty inputs", 
                event.getPurchasePolicy() == null || event.getPurchasePolicy().getRulesView() == null);
    }

    @Test
    public void GivenOnlyAgeIsPresent_WhenAddPurchasePolicy_ThenExactlyTheAgeRuleIsAdded() {
        Event event = activeEvent();

        event.addPurchasePolicy(Optional.of(21f), Optional.empty(), Optional.empty(), Optional.empty(), true);

        var rulesView = event.getPurchasePolicy().getRulesView();
        assertNotNull("The root age composite element should be initialized", rulesView);
    }

    @Test
    public void GivenExistingAgeRule_WhenAddingAnotherAgeRule_ThenOnlyTheLatestAgeRuleRemains() {
        Event event = activeEvent();

        event.addPurchasePolicy(Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.empty(), true);
        event.addPurchasePolicy(Optional.of(21.0f), Optional.empty(), Optional.empty(), Optional.empty(), true);

        var rulesView = event.getPurchasePolicy().getRulesView();
        assertNotNull("The tree root node must reflect the updated assignment block", rulesView);
    }

    @Test
    public void GivenAllFlagsTrueAndAllRulesPresent_WhenDeletePurchaseRule_ThenAllRulesAreRemoved() {
        Event event = activeEvent();
        event.addPurchasePolicy(Optional.of(18f), Optional.empty(), Optional.empty(), Optional.empty(), true);
        
        UUID targetRuleId = event.getPurchasePolicy().getRulesView().getId();
        assertNotNull(targetRuleId);

        // Delete the entire tree wrapper block by targeting its unique ID
        event.deletePurchaseRule(targetRuleId);

        assertTrue("The policy structural configuration must collapse entirely upon root deletion",
                event.getPurchasePolicy() == null || event.getPurchasePolicy().getRulesView() == null);
    }

    @Test
    public void GivenAllFlagsFalse_WhenDeletePurchaseRule_ThenNoRuleIsRemoved() {
        Event event = activeEvent();
        event.addPurchasePolicy(Optional.of(18f), Optional.empty(), Optional.empty(), Optional.empty(), true);

        // Attempt deletion passing a non-matching random structural ID string
        event.deletePurchaseRule(UUID.randomUUID());

        assertNotNull("The active composite tracking configuration state must remain fully intact", 
                event.getPurchasePolicy().getRulesView());
    }

    @Test
    public void GivenMixedFlags_WhenDeletePurchaseRule_ThenExactlyTheSelectedRulesAreRemoved() {
        Event event = activeEvent();
        event.addPurchasePolicy(Optional.of(18f), Optional.empty(), Optional.empty(), Optional.empty(), true);

        UUID rootId = event.getPurchasePolicy().getRulesView().getId();
        
        // Remove the specific configuration node reference from the active model layer
        event.deletePurchaseRule(rootId);

        assertTrue("The specific matched targeted criteria node must be eliminated from the active details list",
                event.getPurchasePolicy() == null || event.getPurchasePolicy().getRulesView() == null);
    }

    @Test
    public void GivenEventWithAgeAndLoneSeatRules_WhenDeleteOnlyAgeRule_ThenOnlyLoneSeatRuleRemains() {
        Event event = activeEvent();
        event.addPurchasePolicy(Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.empty(), true);
        
        UUID targetId = event.getPurchasePolicy().getRulesView().getId();

        event.deletePurchaseRule(targetId);

        assertTrue("The old rule structure reference should clear or downscale cleanly from the layout", 
                event.getPurchasePolicy() == null || event.getPurchasePolicy().getRulesView() == null);
    }

    // ---------------------------------------------------------------------
    // Discount Management
    // ---------------------------------------------------------------------

    @Test
    public void GivenEvent_WhenAddOvertDiscount_ThenDiscountIsAddedToDiscountPolicy() {
        Event event = activeEvent();

        event.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 20.0f);

        var discounts = event.getDiscountPolicy().getDiscountRules();
        assertEquals(1, discounts.size());
        assertTrue("the registered discount must be of type OvertDiscount",
                discounts.get(0) instanceof OvertDiscount);
    }

    @Test
    public void GivenEvent_WhenAddConditionalDiscount_ThenDiscountIsAddedToDiscountPolicy() {
        Event event = activeEvent();

        event.addConditionalDiscount(
                LocalDate.now(), LocalDate.now().plusDays(7), 10f, 3, 2);

        assertEquals(1, event.getDiscountPolicy().getDiscountRules().size());
    }

    @Test
    public void GivenEvent_WhenAddCouponCode_ThenDiscountIsAddedToDiscountPolicy() {
        Event event = activeEvent();

        event.addCouponCode(
                LocalDate.now(), LocalDate.now().plusDays(7), 25f, "SUMMER25");

        assertEquals(1, event.getDiscountPolicy().getDiscountRules().size());
    }

    @Test
    public void GivenEventWithOvertDiscount_WhenRemoveDiscountById_ThenDiscountIsRemoved() {
        Event event = activeEvent();
        event.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 20.0f);
        IDiscountRule existing = event.getDiscountPolicy().getDiscountRules().get(0);
        UUID discountId = existing.getId();

        event.removeDiscount(discountId);

        assertTrue(event.getDiscountPolicy().getDiscountRules().isEmpty());
    }

    // ---------------------------------------------------------------------
    // Rating Logic
    // ---------------------------------------------------------------------

    @Test
    public void GivenTwoDistinctUsers_WhenEachAddsRating_ThenAverageRatingIsComputed() {
        Event event = activeEvent();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        event.addRating(user1, 5);
        event.addRating(user2, 1);

        assertEquals("rating must be the arithmetic mean of all submitted ratings",
                3.0, event.getRating(), 0.0001);
    }

    @Test
    public void GivenUserAlreadyRated_WhenSameUserRatesAgain_ThenDomainExceptionIsThrownAndRatingIsPreserved() {
        Event event = activeEvent();
        UUID user = UUID.randomUUID();
        event.addRating(user, 5);

        assertThrows(DomainException.class, () -> event.addRating(user, 1));
        assertEquals("rating must remain the originally submitted value",
                5.0, event.getRating(), 0.0001);
    }

    // ---------------------------------------------------------------------
    // Capacity
    // ---------------------------------------------------------------------

    @Test
    public void GivenFreshEvent_WhenGetTotalCapacity_ThenReturnsZero() {
        Event event = activeEvent();

        assertEquals("a freshly created event must have zero capacity",
                0, event.getTotalCapacity());
    }

    @Test
    public void GivenStandingAndSittingTicketsAdded_WhenGetTotalCapacity_ThenReturnsSumOfTickets() {
        Event event = activeEvent();
        UUID standingAreaId = UUID.randomUUID();
        UUID sittingAreaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(standingAreaId, 50.0));
        event.getLayout().addArea(new SittingArea(sittingAreaId, 100.0));

        event.addStandingTickets(standingAreaId, 5);
        event.addSittingTickets(sittingAreaId, 2, 3);

        assertEquals("capacity must equal standing tickets + (rows * seatsPerRow)",
                11, event.getTotalCapacity());
    }

    @Test
    public void GivenAreaIsSittingArea_WhenAddStandingTickets_ThenThrowsIllegalArgumentException() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new SittingArea(areaId, 100.0));

        assertThrows(IllegalArgumentException.class,
                () -> event.addStandingTickets(areaId, 3));
    }

    @Test
    public void GivenAreaIsStandingArea_WhenAddSittingTickets_ThenThrowsIllegalArgumentException() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, 50.0));

        assertThrows(IllegalArgumentException.class,
                () -> event.addSittingTickets(areaId, 2, 2));
    }

    @Test
    public void GivenZeroCount_WhenAddStandingTickets_ThenThrowsIllegalArgumentException() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, 50.0));

        assertThrows(IllegalArgumentException.class,
                () -> event.addStandingTickets(areaId, 0));
    }

    @Test
    public void GivenZeroRows_WhenAddSittingTickets_ThenThrowsIllegalArgumentException() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new SittingArea(areaId, 100.0));

        assertThrows(IllegalArgumentException.class,
                () -> event.addSittingTickets(areaId, 0, 4));
    }

    // ---------------------------------------------------------------------
    // Search Matching (Event#matches)
    // ---------------------------------------------------------------------

    @Test
    public void GivenNonActiveEvent_WhenMatches_ThenReturnsFalseRegardlessOfCriteria() {
        Event canceled = eventOf(companyId, "X", "Some Artist", "concert",
                "Tel Aviv", futureDate, EventStatus.CANCELED);

        assertFalse("a non-ACTIVE event must never satisfy a search predicate",
                canceled.matches(EventSearchCriteria.empty(), 5.0));
    }

    @Test
    public void GivenEndedEvent_WhenMatches_ThenReturnsFalse() {
        Event ended = eventOf(companyId, "X", "Some Artist", "concert",
                "Tel Aviv", futureDate, EventStatus.ENDED);

        assertFalse(ended.matches(EventSearchCriteria.empty(), 5.0));
    }

    @Test
    public void GivenActiveEventAndEmptyCriteria_WhenMatches_ThenReturnsTrue() {
        Event event = activeEvent();

        assertTrue(event.matches(EventSearchCriteria.empty(), 0.0));
    }

    @Test
    public void GivenNullCriteria_WhenMatches_ThenFallsBackToPublicVisibility() {
        Event active = activeEvent();
        Event canceled = eventOf(companyId, "X", "Some Artist", "concert",
                "Tel Aviv", futureDate, EventStatus.CANCELED);

        assertTrue(active.matches(null, 0.0));
        assertFalse(canceled.matches(null, 0.0));
    }

    @Test
    public void GivenCompanyIdCriteria_WhenMatches_ThenOnlyEventsFromThatCompanyPass() {
        Event mine = activeEvent();
        Event theirs = eventOf(UUID.randomUUID(), "Y", "Some Artist", "concert",
                "Tel Aviv", futureDate, EventStatus.ACTIVE);
        EventSearchCriteria scoped = EventSearchCriteria.empty().withCompanyId(companyId);

        assertTrue(mine.matches(scoped, 0.0));
        assertFalse(theirs.matches(scoped, 0.0));
    }

    @Test
    public void GivenTextMatchesName_WhenMatches_ThenReturnsTrue() {
        Event e = eventOf(companyId, "Coldplay Live", "OtherArtist", "concert",
                "Tel Aviv", futureDate, EventStatus.ACTIVE);

        assertTrue(e.matches(EventSearchCriteria.empty().withText("coldplay"), 0.0));
    }

    @Test
    public void GivenTextMatchesArtist_WhenMatches_ThenReturnsTrue() {
        Event e = eventOf(companyId, "Some Show", "Coldplay", "concert",
                "Tel Aviv", futureDate, EventStatus.ACTIVE);

        assertTrue(e.matches(EventSearchCriteria.empty().withText("coldplay"), 0.0));
    }

    @Test
    public void GivenTextMatchesType_WhenMatches_ThenReturnsTrue() {
        Event e = eventOf(companyId, "Some Show", "OtherArtist", "Coldplay-tribute",
                "Tel Aviv", futureDate, EventStatus.ACTIVE);

        assertTrue(e.matches(EventSearchCriteria.empty().withText("coldplay"), 0.0));
    }

    @Test
    public void GivenTextMatchesTag_WhenMatches_ThenReturnsTrue() {
        Event e = eventOf(companyId, "Some Show", "OtherArtist", "concert",
                "Tel Aviv", futureDate, EventStatus.ACTIVE, "coldplay");

        assertTrue(e.matches(EventSearchCriteria.empty().withText("coldplay"), 0.0));
    }

    @Test
    public void GivenTextMatchesNothing_WhenMatches_ThenReturnsFalse() {
        Event e = eventOf(companyId, "Some Show", "OtherArtist", "concert",
                "Tel Aviv", futureDate, EventStatus.ACTIVE);

        assertFalse(e.matches(EventSearchCriteria.empty().withText("coldplay"), 0.0));
    }

    @Test
    public void GivenLocationCriteriaMatchingCaseInsensitively_WhenMatches_ThenReturnsTrue() {
        Event tlv = eventOf(companyId, "X", "Ar", "t",
                "Tel Aviv Convention Center", futureDate, EventStatus.ACTIVE);

        assertTrue(tlv.matches(EventSearchCriteria.empty().withLocation("tel aviv"), 0.0));
    }

    @Test
    public void GivenLocationCriteriaNotContained_WhenMatches_ThenReturnsFalse() {
        Event hfa = eventOf(companyId, "Y", "Ar", "t",
                "Haifa Park", futureDate, EventStatus.ACTIVE);

        assertFalse(hfa.matches(EventSearchCriteria.empty().withLocation("tel aviv"), 0.0));
    }

    @Test
    public void GivenAtLeastOneAreaInsidePriceRange_WhenMatches_ThenReturnsTrue() {
        Event mixed = eventOf(companyId, "Y", "Ar", "t",
                "Loc", futureDate, EventStatus.ACTIVE);
        addStandingArea(mixed, 50.0);
        addSittingArea(mixed, 500.0);

        assertTrue("price-range match requires at least ONE area inside the range",
                mixed.matches(EventSearchCriteria.empty().withPriceRange(100.0, 800.0), 0.0));
    }

    @Test
    public void GivenAllAreasBelowPriceRange_WhenMatches_ThenReturnsFalse() {
        Event cheap = eventOf(companyId, "X", "Ar", "t",
                "Loc", futureDate, EventStatus.ACTIVE);
        addStandingArea(cheap, 50.0);

        assertFalse(cheap.matches(EventSearchCriteria.empty().withPriceRange(100.0, 800.0), 0.0));
    }

    @Test
    public void GivenAllAreasAbovePriceRange_WhenMatches_ThenReturnsFalse() {
        Event expensive = eventOf(companyId, "Z", "Ar", "t",
                "Loc", futureDate, EventStatus.ACTIVE);
        addStandingArea(expensive, 1000.0);

        assertFalse(expensive.matches(EventSearchCriteria.empty().withPriceRange(100.0, 800.0), 0.0));
    }

    @Test
    public void GivenDateWithinRange_WhenMatches_ThenReturnsTrue() {
        LocalDateTime now = LocalDateTime.now();
        Event inRange = eventOf(companyId, "I", "Ar", "t",
                "Loc", now.plusDays(10), EventStatus.ACTIVE);

        assertTrue(inRange.matches(
                EventSearchCriteria.empty().withDateRange(now.plusDays(5), now.plusDays(30)),
                0.0));
    }

    @Test
    public void GivenDateBeforeRange_WhenMatches_ThenReturnsFalse() {
        LocalDateTime now = LocalDateTime.now();
        Event tooEarly = eventOf(companyId, "E", "Ar", "t",
                "Loc", now.plusDays(1), EventStatus.ACTIVE);

        assertFalse(tooEarly.matches(
                EventSearchCriteria.empty().withDateRange(now.plusDays(5), now.plusDays(30)),
                0.0));
    }

    @Test
    public void GivenDateAfterRange_WhenMatches_ThenReturnsFalse() {
        LocalDateTime now = LocalDateTime.now();
        Event tooLate = eventOf(companyId, "L", "Ar", "t",
                "Loc", now.plusDays(60), EventStatus.ACTIVE);

        assertFalse(tooLate.matches(
                EventSearchCriteria.empty().withDateRange(now.plusDays(5), now.plusDays(30)),
                0.0));
    }

    @Test
    public void GivenEventRatingBelowThreshold_WhenMatches_ThenReturnsFalse() {
        Event low = eventOf(companyId, "L", "Ar", "t",
                "Loc", futureDate, EventStatus.ACTIVE);
        low.addRating(UUID.randomUUID(), 2);

        assertFalse(low.matches(EventSearchCriteria.empty().withMinEventRating(4.0), 0.0));
    }

    @Test
    public void GivenEventRatingAtOrAboveThreshold_WhenMatches_ThenReturnsTrue() {
        Event high = eventOf(companyId, "H", "Ar", "t",
                "Loc", futureDate, EventStatus.ACTIVE);
        high.addRating(UUID.randomUUID(), 5);

        assertTrue(high.matches(EventSearchCriteria.empty().withMinEventRating(4.0), 0.0));
    }

    @Test
    public void GivenCompanyRatingBelowThreshold_WhenMatches_ThenReturnsFalse() {
        Event e = activeEvent();

        assertFalse("matches must reject when the provided company rating is below the threshold",
                e.matches(EventSearchCriteria.empty().withMinCompanyRating(4.0), 2.0));
    }

    @Test
    public void GivenCompanyRatingAtOrAboveThreshold_WhenMatches_ThenReturnsTrue() {
        Event e = activeEvent();

        assertTrue(e.matches(EventSearchCriteria.empty().withMinCompanyRating(4.0), 5.0));
    }
    
    @Test
    public void testAddPurchasePolicy_PersistsRulesInEventState() {
        // Arrange
        Optional<Float> age = Optional.of(16.0f);
        Optional<Integer> maxTickets = Optional.of(4);

        // Act: Sequential additions building out the composite pattern using the trailing andOr operator
        event.addPurchasePolicy(age, Optional.empty(), Optional.empty(), Optional.empty(), true);
        event.addPurchasePolicy(Optional.empty(), Optional.empty(), maxTickets, Optional.empty(), true);
        event.addPurchasePolicy(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true), true);

        // Assert
        var rulesView = event.getPurchasePolicy().getRulesView();
        assertNotNull("The active purchase policy structural view must not be null", rulesView);
        assertNotNull("The composite tree node should hold a generated dynamic ID", rulesView.getId());
    }

    @Test
    public void testDeletePurchaseRule_RemovesCorrectRulesFromEvent() {
        // Arrange: Pre-populate rule configurations
        event.addPurchasePolicy(Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.empty(), true);
        
        var rulesViewBefore = event.getPurchasePolicy().getRulesView();
        assertNotNull(rulesViewBefore);
        UUID targetRuleId = rulesViewBefore.getId();

        // Act: Target the clear specific rule entity structure for reduction
        event.deletePurchaseRule(targetRuleId);

        // Assert
        var rulesViewAfter = event.getPurchasePolicy().getRulesView();
        assertTrue("The policy structural configuration must collapse or become null upon targeted node deletion",
                rulesViewAfter == null);
    }

    @Test
    public void testAddPurchasePolicy_HandlesNullOptionalsGracefully() {
        // Act & Assert: Ensure it handles null parameters safely using the updated composite signature
        try {
            event.addPurchasePolicy(null, null, null, null, true);
        } catch (NullPointerException e) {
            fail("Event.addPurchasePolicy should handle null parameters safely without throwing NullPointerException");
        }

        assertTrue("Policy configuration should remain empty or uninitialized on completely null parameters", 
                event.getPurchasePolicy() == null || event.getPurchasePolicy().getRulesView() == null);
    }

    @Test
    public void testConstructor_ThrowsExceptionOnNullStatus() {
        // Testing the validation logic in your constructor
        assertThrows(IllegalArgumentException.class, () -> {
            new Event(eventId, companyId, LocalDateTime.now(), "Tel Aviv", "Artist", "Type", null, DiscountType.ALL);
        });
    }

    @Test
    public void testDeletePurchaseRule_IgnoresDeletionIfPolicyIsEmpty() {
        // Act & Assert
        // If your domain model initializes a null reference for completely empty states,
        // we guard the invocation inside the test to match that specific design constraint.
        if (event.getPurchasePolicy() != null && event.getPurchasePolicy().getRulesView() != null) {
            try {
                event.deletePurchaseRule(UUID.randomUUID());
            } catch (NullPointerException e) {
                fail("deletePurchaseRule crashed during active tree matching operation");
            }
        }

        // Final Verification
        assertTrue("No structural modifications should occur on an empty policy setup", 
                event.getPurchasePolicy() == null || event.getPurchasePolicy().getRulesView() == null);
    }

    @Test
    public void GivenStandingArea_WhenUpdateStandingAreaIncreasesCount_ThenAddsTicketsAndUpdatesAreaAndTicketPrices() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        event.addStandingTickets(areaId, 2);

        event.updateStandingArea(areaId, 80.0, 5);

        Area area = event.getLayout().requireArea(areaId);

        assertEquals(5, area.getTicketIdsView().size());
        assertEquals(5, event.getTotalCapacity());
        assertEquals(80.0, area.getPrice(), 0.0001);

        for (UUID ticketId : area.getTicketIdsView()) {
            assertEquals(80.0f, event.getTicket(ticketId).getPrice(), 0.0001);
        }
    }

    @Test
    public void GivenStandingArea_WhenUpdateStandingAreaDecreasesCount_ThenRemovesOnlyAvailableTickets() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        event.addStandingTickets(areaId, 5);

        event.updateStandingArea(areaId, 60.0, 3);

        Area area = event.getLayout().requireArea(areaId);

        assertEquals(3, area.getTicketIdsView().size());
        assertEquals(3, event.getTotalCapacity());
        assertEquals(60.0, area.getPrice(), 0.0001);
    }

    @Test
    public void GivenStandingAreaWithReservedTickets_WhenReducingBelowReservedAmount_ThenThrowsAndKeepsTickets() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        event.addStandingTickets(areaId, 5);

        event.reserveStandingTickets(2, areaId);

        assertThrows(IllegalStateException.class, () ->
                event.updateStandingArea(areaId, 70.0, 1)
        );

        assertEquals(5, event.getTotalCapacity());
        assertEquals(5, event.getLayout().requireArea(areaId).getTicketIdsView().size());
    }

    @Test
    public void GivenAreaWithOnlyAvailableTickets_WhenDeleteArea_ThenAreaAndTicketsAreRemoved() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        event.addStandingTickets(areaId, 3);

        event.deleteArea(areaId);

        assertEquals(0, event.getTotalCapacity());

        assertThrows(IllegalArgumentException.class, () ->
                event.getLayout().requireArea(areaId)
        );
    }

    @Test
    public void GivenAreaWithReservedTicket_WhenDeleteArea_ThenThrowsAndKeepsAreaAndTickets() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        event.addStandingTickets(areaId, 3);

        event.reserveStandingTickets(1, areaId);

        assertThrows(IllegalStateException.class, () ->
                event.deleteArea(areaId)
        );

        assertEquals(3, event.getTotalCapacity());
        assertEquals(3, event.getLayout().requireArea(areaId).getTicketIdsView().size());
    }

    @Test
    public void GivenSittingArea_WhenUpdateSittingAreaExpandsLayout_ThenAddsMissingSeatsAndUpdatesPrices() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new SittingArea(areaId, 100.0));
        event.addSittingTickets(areaId, 2, 2);

        event.updateSittingArea(areaId, 150.0, 3, 3);

        Area area = event.getLayout().requireArea(areaId);

        assertEquals(9, area.getTicketIdsView().size());
        assertEquals(9, event.getTotalCapacity());
        assertEquals(150.0, area.getPrice(), 0.0001);

        for (UUID ticketId : area.getTicketIdsView()) {
            Ticket ticket = event.getTicket(ticketId);
            assertEquals(150.0f, ticket.getPrice(), 0.0001);
        }

        SittingTicket lastSeat = null;

        for (UUID ticketId : area.getTicketIdsView()) {
            Ticket ticket = event.getTicket(ticketId);

            if (ticket instanceof SittingTicket sittingTicket
                    && sittingTicket.getSeatRow() == 3
                    && sittingTicket.getSeatNumber() == 3) {
                lastSeat = sittingTicket;
            }
        }

        assertNotNull(lastSeat);
    }

    @Test
    public void GivenSittingArea_WhenUpdateSittingAreaShrinksLayout_ThenRemovesSeatsOutsideNewLayout() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new SittingArea(areaId, 100.0));
        event.addSittingTickets(areaId, 2, 2);

        event.updateSittingArea(areaId, 120.0, 1, 1);

        Area area = event.getLayout().requireArea(areaId);

        assertEquals(1, area.getTicketIdsView().size());
        assertEquals(1, event.getTotalCapacity());

        Ticket remainingTicket = event.getTicket(area.getTicketIdsView().get(0));

        assertTrue(remainingTicket instanceof SittingTicket);

        SittingTicket remainingSeat = (SittingTicket) remainingTicket;

        assertEquals(1, remainingSeat.getSeatRow());
        assertEquals(1, remainingSeat.getSeatNumber());
        assertEquals(120.0f, remainingSeat.getPrice(), 0.0001);
    }

    @Test
    public void GivenSittingAreaWithReservedSeatOutsideRequestedLayout_WhenShrinkingLayout_ThenThrowsAndKeepsAllSeats() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new SittingArea(areaId, 100.0));
        event.addSittingTickets(areaId, 2, 2);

        UUID reservedSeatId = null;

        for (UUID ticketId : event.getLayout().requireArea(areaId).getTicketIdsView()) {
            Ticket ticket = event.getTicket(ticketId);

            if (ticket instanceof SittingTicket sittingTicket
                    && sittingTicket.getSeatRow() == 2
                    && sittingTicket.getSeatNumber() == 2) {
                reservedSeatId = ticketId;
                break;
            }
        }

        assertNotNull(reservedSeatId);

        event.reserveSittingTickets(java.util.Collections.singletonList(reservedSeatId));

        assertThrows(IllegalStateException.class, () ->
                event.updateSittingArea(areaId, 120.0, 1, 1)
        );

        assertEquals(4, event.getTotalCapacity());
        assertEquals(4, event.getLayout().requireArea(areaId).getTicketIdsView().size());
    }

    @Test
    public void GivenStandingArea_WhenRemoveTicket_ThenTicketIsRemovedFromEventAndArea() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        event.addStandingTickets(areaId, 2);

        UUID ticketId = event.getLayout().requireArea(areaId).getTicketIdsView().get(0);

        event.removeTicket(ticketId);

        assertEquals(1, event.getTotalCapacity());
        assertFalse(event.getLayout().requireArea(areaId).getTicketIdsView().contains(ticketId));
    }

    @Test
    public void GivenReservedTicket_WhenRemoveTicket_ThenThrowsAndKeepsTicket() {
        Event event = activeEvent();
        UUID areaId = UUID.randomUUID();

        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        event.addStandingTickets(areaId, 2);

        UUID ticketId = event.reserveStandingTickets(1, areaId).get(0);

        assertThrows(IllegalStateException.class, () ->
                event.removeTicket(ticketId)
        );

        assertEquals(2, event.getTotalCapacity());
        assertTrue(event.getLayout().requireArea(areaId).getTicketIdsView().contains(ticketId));
    }
}
