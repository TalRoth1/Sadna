package org.example.DomainLayer.EventAggregate;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PolicyAggregate.AgeRule;
import org.example.DomainLayer.PolicyAggregate.ConditionalDiscount;
import org.example.DomainLayer.PolicyAggregate.CouponCode;
import org.example.DomainLayer.PolicyAggregate.DiscountPolicy;
import org.example.DomainLayer.PolicyAggregate.LoneSeatRule;
import org.example.DomainLayer.PolicyAggregate.MaxTicketRule;
import org.example.DomainLayer.PolicyAggregate.MinTicketRule;
import org.example.DomainLayer.PolicyAggregate.OvertDiscount;
import org.example.DomainLayer.PolicyAggregate.PurchasePolicy;
import org.example.DomainLayer.Rating;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Event {

    private final UUID eventId;
    private final UUID companyId;
    private LocalDateTime date;
    private String location;
    private final List<String> tags = new ArrayList<>();
    private EventStatus status;
    private String artist;
    private String type;
    private final Layout layout;
    private final PurchasePolicy purchasePolicy;
    private final DiscountPolicy discountPolicy;
    private double rating;
    private String lotteryId;
    private final Map<UUID, Ticket> ticketsById = new LinkedHashMap<>();
    private Map<UUID, Rating> ratingsByUsers = new LinkedHashMap<>();

    public Event(UUID eventId, UUID companyId, LocalDateTime date, String location,
                   String artist, String type, EventStatus status) {
        this.eventId = eventId;
        this.companyId = companyId;
        setDate(date);
        setLocation(location);
        setArtist(artist);
        setType(type);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.rating = 0;
        this.layout = new Layout();
        this.purchasePolicy = new PurchasePolicy();
        this.discountPolicy = new DiscountPolicy();
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        this.date = date;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location required");
        }
        this.location = location.trim();
    }

    public List<String> getTagsView() {
        return List.copyOf(tags);
    }

    public void setTags(List<String> tags) {
        this.tags.clear();
        if (tags != null) {
            for (String t : tags) {
                if (t != null && !t.isBlank()) {
                    this.tags.add(t.trim());
                }
            }
        }
    }

    public void addTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag required");
        }
        tags.add(tag.trim());
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        if (artist == null || artist.isBlank()) {
            throw new IllegalArgumentException("artist required");
        }
        this.artist = artist.trim();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type required");
        }
        this.type = type.trim();
    }

    public Layout getLayout() {
        return layout;
    }

    public PurchasePolicy getPurchasePolicy() {
        return purchasePolicy;
    }

    public DiscountPolicy getDiscountPolicy() {
        return discountPolicy;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        if (rating < 0) {
            throw new IllegalArgumentException("rating must be non-negative");
        }
        this.rating = rating;
    }

    public String getLotteryId()
    {
        return lotteryId;
    }

    public void setLotteryId(String lotteryId) {
        this.lotteryId = lotteryId;
    }

    public Map<UUID, Ticket> getTicketsView() {
        return Map.copyOf(ticketsById);
    }

    public synchronized void addTicket(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("ticket must not be null");
        }
        if (!(eventId == ticket.getEventId())) {
            throw new IllegalArgumentException("ticket event mismatch");
        }
        UUID tid = ticket.getTicketId();
        if (ticketsById.containsKey(tid)) {
            throw new IllegalStateException("duplicate ticket id: " + tid);
        }
        layout.requireArea(ticket.getAreaId()).linkTicketId(tid);
        ticketsById.put(tid, ticket);
    }

    public Ticket getTicket(UUID ticketId) {
        return ticketsById.get(ticketId);
    }

    public void checkAvailabilityOfSittingTickets(List<UUID> ticketIDs) {
        if (ticketIDs == null || ticketIDs.isEmpty()) {
            throw new DomainException("Ticket id list is empty");
        }

        for (UUID tid : ticketIDs) {
            Ticket t = ticketsById.get(tid);

            if (t == null) {
                throw new DomainException("Ticket " + tid + " does not exist for this event");
            }

            if (t.getStatus() != TicketStatus.AVAILABLE) {
                throw new DomainException("Ticket " + tid + " is not available");
            }
        }
    }


    public void checkAvailabilityOfStandingTickets(int amount, UUID areaID)
    {
        Area area = layout.requireArea(areaID);

        List<UUID> areaTicketIds = area.getTicketIdsView();

        long availableCount = areaTicketIds.stream()
                .map(ticketsById::get) 
                .filter(t -> t != null && t.getStatus() == TicketStatus.AVAILABLE)
                .count();

        if (availableCount < amount) {
            throw new DomainException("Not enough available tickets in the requested area");
        }
    }

    public synchronized void reserveSittingTickets(List<UUID> ticketIDs)
    {
        List<Ticket> ticketsToReserve = new ArrayList<>();

        for (UUID id : ticketIDs) {
            Ticket ticket = ticketsById.get(id);

            if (ticket == null) {
                throw new DomainException("Ticket id " + id + " does not exist for this event");
            }

            if (ticket.getStatus() != TicketStatus.AVAILABLE) {
                throw new DomainException("The selected seat is no longer available");
            }

            ticketsToReserve.add(ticket);
        }

        for (Ticket ticket : ticketsToReserve) {
            ticket.reserve();
        }
    }

    public synchronized List<UUID> reserveStandingTickets(int amount, UUID areaId) {
        Area area = layout.requireArea(areaId);
        List<UUID> areaTicketIds = area.getTicketIdsView();

        List<UUID> selectedTickets = areaTicketIds.stream()
                .map(ticketsById::get)
                .filter(t -> t != null && t.getStatus() == TicketStatus.AVAILABLE)
                .limit(amount)
                .map(Ticket::getTicketId)
                .toList();

        if (selectedTickets.size() < amount) {
            throw new DomainException("Not enough available tickets in the requested area");
        }
        for (UUID tid : selectedTickets) {
            ticketsById.get(tid).reserve();
        }

        return selectedTickets;
    }

    public void addPurchasePolicy(Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat)
    {
    if (age != null && age.isPresent()) 
        this.purchasePolicy.addRule(new AgeRule(age.get()));
        
    if (minTicket != null && minTicket.isPresent()) 
        this.purchasePolicy.addRule(new MinTicketRule(minTicket.get()));
        
    if (maxTicket != null && maxTicket.isPresent()) 
        this.purchasePolicy.addRule(new MaxTicketRule(maxTicket.get()));
        
    if (allowLoneSeat != null && allowLoneSeat.isPresent()) 
        this.purchasePolicy.addRule(new LoneSeatRule(allowLoneSeat.get()));
    }

    public void deletePurchaseRule(boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat)
    {
        if(age)
            this.purchasePolicy.removeRule(new AgeRule(0));
        if(minTicket)
            this.purchasePolicy.removeRule(new MinTicketRule(0));
        if(maxTicket)
            this.purchasePolicy.removeRule(new MaxTicketRule(0));
        if(allowLoneSeat)
            this.purchasePolicy.removeRule(new LoneSeatRule(false));
    }

        public void addOvertDiscount(LocalDate fromDate, LocalDate toDate, float discountPrecent)
    {
        this.discountPolicy.addRule(new OvertDiscount(discountPrecent, fromDate, toDate));
    }

    public void addConditionalDiscount(LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets)
    {
        this.discountPolicy.addRule(new ConditionalDiscount(fromDate, toDate, discountPrecent, requiredTickets, appliedTickets));
    }

    public void addCouponCode(LocalDate fromDate, LocalDate toDate, float discountPrecent, String code)
    {
        this.discountPolicy.addRule(new CouponCode(fromDate, toDate, discountPrecent, code));
    }

    public void removeDiscount(UUID discountId)
    {
        this.discountPolicy.removeRule(discountId);
    }

    public synchronized void releaseTickets(Map<UUID, Float> ticketIDs) {
        for (Map.Entry<UUID, Float> tid : ticketIDs.entrySet()) {
            Ticket ticket = ticketsById.get(tid.getKey());
            if (ticket != null && ticket.getStatus() == TicketStatus.RESERVED) {
                ticket.releaseReservation();
            }
        }
    }
    public synchronized void sellTickets(Set<UUID> ticketIDs) {
        for (UUID tid : ticketIDs) {
            Ticket ticket = ticketsById.get(tid);
            if (ticket != null) {
                ticket.markSold();
            }
        }
    }
    public double calculateTotalPrice(List<UUID> ticketIDs)
    {
        double totalPrice = 0;

        for (UUID tid : ticketIDs) {
            Ticket ticket = ticketsById.get(tid);
            if (ticket == null) {
                throw new DomainException("הכרטיס " + tid + " לא קיים באירוע");
            }
            totalPrice += ticket.getPrice();
        }

        return totalPrice;
    }

    public synchronized void addRating(UUID userID, int rating) {
        if (ratingsByUsers.containsKey(userID))
            throw new DomainException("User already reviewed this event");
        else {
            Rating r = new Rating(rating, userID);
            ratingsByUsers.put(userID, r);

            double sum = 0;

            for (Rating existingRating : ratingsByUsers.values()) {
                sum += existingRating.getRating();
            }

            this.rating = sum / ratingsByUsers.size();
        }
    }

    public int getTotalCapacity() {
        return ticketsById.size();
    }

    public void addStandingTickets(UUID areaId, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        Area area = layout.requireArea(areaId);
        if (!(area instanceof StandingArea)) {
            throw new IllegalArgumentException("area is not a standing area: " + areaId);
        }
        float price = (float) area.getPrice();
        for (int i = 0; i < count; i++) {
            UUID ticketId = UUID.randomUUID();
            StandingTicket ticket = new StandingTicket(ticketId, eventId, areaId, price);
            addTicket(ticket);
        }
    }

    public void addSittingTickets(UUID areaId, int rows, int seatsPerRow) {
        if (rows <= 0 || seatsPerRow <= 0) {
            throw new IllegalArgumentException("rows and seatsPerRow must be positive");
        }
        Area area = layout.requireArea(areaId);
        if (!(area instanceof SittingArea)) {
            throw new IllegalArgumentException("area is not a sitting area: " + areaId);
        }
        float price = (float) area.getPrice();
        for (int row = 1; row <= rows; row++) {
            for (int seat = 1; seat <= seatsPerRow; seat++) {
                UUID ticketId = UUID.randomUUID();
                SittingTicket ticket = new SittingTicket(ticketId, eventId, areaId, price, seat, row);
                addTicket(ticket);
            }
        }
    }
}
