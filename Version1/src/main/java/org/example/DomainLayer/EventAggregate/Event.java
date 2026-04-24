package org.example.DomainLayer.EventAggregate;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.Rating;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event aggregate root: 1:1 {@link Layout}, {@link PurchasePolicy}, {@link DiscountPolicy}; 1:* {@link Ticket}.
 * Optional {@code lotteryId} links to a lottery mechanism elsewhere in the system.
 */
public class Event {

    private final int eventId;
    private final int companyId;
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
    private final Map<Integer, Ticket> ticketsById = new LinkedHashMap<>();

    private Map<String, Rating> ratingsByUsers = new LinkedHashMap<>();

    public Event(int eventId, int companyId, LocalDateTime date, String location,
                   String artist, String type, EventStatus status, double rating) {
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
        if (rating < 0) {
            throw new IllegalArgumentException("rating must be non-negative");
        }
        this.rating = rating;
        this.layout = new Layout();
        this.purchasePolicy = new PurchasePolicy();
        this.discountPolicy = new DiscountPolicy();
    }

    public int getEventId() {
        return eventId;
    }

    public int getCompanyId() {
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

    public Map<Integer, Ticket> getTicketsView() {
        return Map.copyOf(ticketsById);
    }

    /**
     * Registers a ticket on the event and links its id to the given layout area (Area 1:* Ticket by id).
     */
    public void addTicket(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("ticket must not be null");
        }
        if (!(eventId == ticket.getEventId())) {
            throw new IllegalArgumentException("ticket event mismatch");
        }
        int tid = ticket.getTicketId();
        if (ticketsById.containsKey(tid)) {
            throw new IllegalStateException("duplicate ticket id: " + tid);
        }
        layout.requireArea(ticket.getAreaId()).linkTicketId(tid);
        ticketsById.put(tid, ticket);
    }

    public Ticket getTicket(String ticketId) {
        if (ticketId == null) {
            throw new IllegalArgumentException("ticketId must not be null");
        }
        return ticketsById.get(ticketId);
    }

    public void checkAvailabilityOfSittingTickets(List<Integer> ticketIDs) {
        if (ticketIDs == null || ticketIDs.isEmpty()) {
            throw new DomainException("רשימת הכרטיסים שהוזנה ריקה");
        }

        for (int tid : ticketIDs) {
            Ticket t = ticketsById.get(tid);

            // בדיקה 1: האם הכרטיס בכלל קיים באירוע הזה?
            if (t == null) {
                throw new DomainException("הכרטיס " + tid + "לא קיים באירוע");

            }

            // בדיקה 2: האם הוא בסטטוס פנוי?
            if (t.getStatus() != TicketStatus.AVAILABLE) {
                throw new DomainException("הכרטיס " + tid + "לא פנוי");

            }
        }
    }


    public void checkAvailabilityOfStandingTickets(int amount, int areaID)
    {
        Area area = layout.requireArea(areaID);

        // 2. שולף את כל ה-IDs של הכרטיסים ששייכים לאזור הזה
        List<Integer> areaTicketIds = area.getTicketIdsView();

        // 3. סופר כמה מהם בסטטוס AVAILABLE בתוך ה-Map של האירוע
        long availableCount = areaTicketIds.stream()
                .map(ticketsById::get) // שואב את אובייקט ה-Ticket לפי ה-ID
                .filter(t -> t != null && t.getStatus() == TicketStatus.AVAILABLE)
                .count();

        // 4. בודק אם יש מספיק כרטיסים פנויים לפי הכמות המבוקשת
        if (availableCount < amount) {
            // ה-Event זורק את השגיאה כי הוא זה שגילה שהמלאי חסר
            throw new DomainException("אין מספיק כרטיסים פנויים באזור המבוקש");
        }
    }

    public void reserveSittingTickets(List<Integer> ticketIDs)
    {
        List<Ticket> ticketsToReserve = new ArrayList<>();

        for (int id : ticketIDs) {
            Ticket ticket = ticketsById.get(id);

            // הגנה: האם הכרטיס בכלל קיים באירוע הזה?
            if (ticket == null) {
                throw new DomainException("כרטיס שמזההו " + id + " אינו קיים באירוע זה.");
            }

            // הגנה: האם הכרטיס פנוי?
            if (ticket.getStatus() != TicketStatus.AVAILABLE) {
                throw new DomainException("הכיסא שנבחר כבר אינו פנוי.");
            }

            ticketsToReserve.add(ticket);
        }

        // 2. שלב הביצוע - רק אם כל הכרטיסים עברו את הבדיקה, נשריין אותם
        for (Ticket ticket : ticketsToReserve) {
            ticket.reserve(); // המתודה הפנימית ב-Ticket שמשנה ל-RESERVED
        }
    }

    public List<Integer> reserveStandingTickets(int amount, int areaId) {
        Area area = layout.requireArea(areaId);
        List<Integer> areaTicketIds = area.getTicketIdsView();

        // מוצאים כרטיסים פנויים מתוך הרשימה של האזור
        List<Integer> selectedTickets = areaTicketIds.stream()
                .map(ticketsById::get)
                .filter(t -> t != null && t.getStatus() == TicketStatus.AVAILABLE)
                .limit(amount) // לוקחים רק את הכמות המבוקשת
                .map(Ticket::getTicketId)
                .toList();

        // בדיקה: האם הצלחנו למצוא מספיק כרטיסים?
        if (selectedTickets.size() < amount) {
            throw new DomainException("אין מספיק כרטיסים פנויים באזור המבוקש");
        }

        // מבצעים את השריון בפועל לכל אחד מהנבחרים
        for (int tid : selectedTickets) {
            ticketsById.get(tid).reserve();
        }

        return selectedTickets; // מחזירים את ה-IDs ל-Service
    }

    public synchronized void addRating(String userID, int rating) {
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
}
