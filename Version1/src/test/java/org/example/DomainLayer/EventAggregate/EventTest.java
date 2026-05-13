package org.example.DomainLayer.EventAggregate;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.IDiscountRule;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

    private UUID eventId;
    private UUID companyId;
    private LocalDateTime futureDate;

    @Before
    public void setUp() {
        eventId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        futureDate = LocalDateTime.now().plusDays(10);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Event activeEvent() {
        return new Event(eventId, companyId, futureDate, "Tel Aviv",
                "Some Artist", "concert", EventStatus.ACTIVE);
    }

    private Event eventOf(UUID compId, String name, String artist, String type,
                          String location, LocalDateTime date,
                          EventStatus status, String... tags) {
        Event e = new Event(UUID.randomUUID(), compId, date, location, artist, type, status);
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

        event.addPurchasePolicy(
                Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));

        var rules = event.getPurchasePolicy().getRulesView();
        assertEquals("one rule must be produced per provided optional", 4, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r instanceof AgeRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof MinTicketRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof MaxTicketRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof LoneSeatRule));
    }

    @Test
    public void GivenAgeAndAllowLoneSeatOptionalsPresent_WhenAddPurchasePolicy_ThenOnlyThoseTwoRulesArePersisted() {
        Event event = activeEvent();

        event.addPurchasePolicy(
                Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.of(true));

        var rules = event.getPurchasePolicy().getRulesView();
        assertEquals(2, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r instanceof AgeRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof LoneSeatRule));
    }

    @Test
    public void GivenAllOptionalsEmpty_WhenAddPurchasePolicy_ThenNoRuleIsAdded() {
        Event event = activeEvent();

        event.addPurchasePolicy(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals("an empty optional means 'no rule for this field'",
                0, event.getPurchasePolicy().getRulesView().size());
    }

    @Test
    public void GivenOnlyAgeIsPresent_WhenAddPurchasePolicy_ThenExactlyTheAgeRuleIsAdded() {
        Event event = activeEvent();

        event.addPurchasePolicy(
                Optional.of(21f), Optional.empty(), Optional.empty(), Optional.empty());

        var rules = event.getPurchasePolicy().getRulesView();
        assertEquals(1, rules.size());
        assertTrue(rules.get(0) instanceof AgeRule);
    }

    @Test
    public void GivenExistingAgeRule_WhenAddingAnotherAgeRule_ThenOnlyTheLatestAgeRuleRemains() {
        Event event = activeEvent();

        event.addPurchasePolicy(
                Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.empty());
        event.addPurchasePolicy(
                Optional.of(21.0f), Optional.empty(), Optional.empty(), Optional.empty());

        var rules = event.getPurchasePolicy().getRulesView();
        long ageRuleCount = rules.stream().filter(r -> r instanceof AgeRule).count();
        assertEquals("a second age rule must replace the first, never duplicate",
                1, ageRuleCount);
        assertEquals("the surviving age rule must carry the latest value",
                21.0f, ((AgeRule) rules.get(0)).getMinAge(), 0.001);
    }

    @Test
    public void GivenAllFlagsTrueAndAllRulesPresent_WhenDeletePurchaseRule_ThenAllRulesAreRemoved() {
        Event event = activeEvent();
        event.addPurchasePolicy(
                Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));
        assertEquals(4, event.getPurchasePolicy().getRulesView().size());

        event.deletePurchaseRule(true, true, true, true);

        assertEquals(0, event.getPurchasePolicy().getRulesView().size());
    }

    @Test
    public void GivenAllFlagsFalse_WhenDeletePurchaseRule_ThenNoRuleIsRemoved() {
        Event event = activeEvent();
        event.addPurchasePolicy(
                Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));

        event.deletePurchaseRule(false, false, false, false);

        assertEquals(4, event.getPurchasePolicy().getRulesView().size());
    }

    @Test
    public void GivenMixedFlags_WhenDeletePurchaseRule_ThenExactlyTheSelectedRulesAreRemoved() {
        Event event = activeEvent();
        event.addPurchasePolicy(
                Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));

        event.deletePurchaseRule(true, false, true, false);

        assertEquals("only the flagged-true rule classes must be removed",
                2, event.getPurchasePolicy().getRulesView().size());
    }

    @Test
    public void GivenEventWithAgeAndLoneSeatRules_WhenDeleteOnlyAgeRule_ThenOnlyLoneSeatRuleRemains() {
        Event event = activeEvent();
        event.addPurchasePolicy(
                Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.of(false));

        event.deletePurchaseRule(true, false, false, false);

        var rules = event.getPurchasePolicy().getRulesView();
        assertEquals(1, rules.size());
        assertTrue(rules.get(0) instanceof LoneSeatRule);
    }

    // ---------------------------------------------------------------------
    // Discount Management
    // ---------------------------------------------------------------------

    @Test
    public void GivenEvent_WhenAddOvertDiscount_ThenDiscountIsAddedToDiscountPolicy() {
        Event event = activeEvent();

        event.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 20.0f);

        var discounts = event.getDiscountPolicy().gDiscountRules();
        assertEquals(1, discounts.size());
        assertTrue("the registered discount must be of type OvertDiscount",
                discounts.get(0) instanceof OvertDiscount);
    }

    @Test
    public void GivenEvent_WhenAddConditionalDiscount_ThenDiscountIsAddedToDiscountPolicy() {
        Event event = activeEvent();

        event.addConditionalDiscount(
                LocalDate.now(), LocalDate.now().plusDays(7), 10f, 3, 2);

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
    }

    @Test
    public void GivenEvent_WhenAddCouponCode_ThenDiscountIsAddedToDiscountPolicy() {
        Event event = activeEvent();

        event.addCouponCode(
                LocalDate.now(), LocalDate.now().plusDays(7), 25f, "SUMMER25");

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
    }

    @Test
    public void GivenEventWithOvertDiscount_WhenRemoveDiscountById_ThenDiscountIsRemoved() {
        Event event = activeEvent();
        event.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 20.0f);
        IDiscountRule existing = event.getDiscountPolicy().gDiscountRules().get(0);
        UUID discountId = existing.getId();

        event.removeDiscount(discountId);

        assertTrue(event.getDiscountPolicy().gDiscountRules().isEmpty());
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
}
