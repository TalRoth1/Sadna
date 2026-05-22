package org.example.API;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.ILotteryRepository;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a few demo events the first time the app boots with an empty
 * repository, so the React Event Search page actually has data to show
 * during the dev loop. Wrapped in the {@code dev} profile (active by
 * default — see {@code application.properties}) so production builds
 * don't accidentally insert demo rows.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger logger = Logger.getLogger(DevDataSeeder.class.getName());

    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final ILotteryRepository lotteryRepository;
    private final EventManagementDomainService eventManagement;

    public DevDataSeeder(IEventRepository eventRepository,
                         ICompanyRepository companyRepository,
                         ILotteryRepository lotteryRepository,
                         EventManagementDomainService eventManagement) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.lotteryRepository = lotteryRepository;
        this.eventManagement = eventManagement;
    }

    @Override
    public void run(String... args) {
        if (!eventRepository.getAll().isEmpty()) {
            logger.info("[DevDataSeeder] repository already has events — skipping seed.");
            return;
        }

        UUID liveNationId = companyRepository.createCompany("admin", "Live Nation");
        UUID indieProdId  = companyRepository.createCompany("admin", "Indie Productions");

        seedEvent(liveNationId, "Coldplay – Music of the Spheres",
                "Coldplay", "Concert", "Tel Aviv",
                LocalDateTime.now().plusDays(30), 350.0, 200);

        seedLotteryEvent(liveNationId, "Taylor Swift – Lottery Night",
                "Taylor Swift", "Concert", "Park Hayarkon, Tel Aviv",
                LocalDateTime.now().plusDays(45), 450.0, 100);

        seedEvent(liveNationId, "Hapoel TLV vs Maccabi",
                "Hapoel Tel Aviv", "Sports", "Bloomfield Stadium, Tel Aviv",
                LocalDateTime.now().plusDays(7), 120.0, 500);

        seedEvent(indieProdId, "Stand-up Night with Adir Miller",
                "Adir Miller", "Comedy", "Habima Theatre, Tel Aviv",
                LocalDateTime.now().plusDays(14), 180.0, 80);

        seedEvent(indieProdId, "Jazz at the Cellar",
                "Avishai Cohen Trio", "Jazz", "Beit Haamudim, Tel Aviv",
                LocalDateTime.now().plusDays(21), 220.0, 60);

        logger.info("[DevDataSeeder] seeded " + eventRepository.getAll().size() + " demo events.");
    }

    /**
     * Creates one ACTIVE event with a single standing area and a pool of
     * tickets so price-range / availability filters have something to bite
     * on. Kept intentionally simple — no sittings, no discounts, no policies.
     */
    private void seedEvent(UUID companyId, String name, String artist, String type,
                           String location, LocalDateTime date, double price, int capacity) {
        UUID eventId = UUID.randomUUID();
        eventManagement.addEvent(eventId, companyId, name, date, location, artist, type, EventStatus.ACTIVE);

        Event event = eventRepository.getById(eventId);
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, price));
        eventManagement.addStandingTickets(eventId, areaId, capacity);
    }

    /**
     * Creates one ACTIVE lottery event.
     *
     * A lottery event needs both:
     *  1. An Event with lotteryId set, so the frontend can identify it as a lottery event.
     *  2. A matching PuchaseLottery saved by eventId, so registration can actually work.
     */
    private void seedLotteryEvent(UUID companyId, String name, String artist, String type,
                                  String location, LocalDateTime date, double price, int capacity) {
        UUID eventId = UUID.randomUUID();
        eventManagement.addEvent(eventId, companyId, name, date, location, artist, type, EventStatus.ACTIVE);

        Event event = eventRepository.getById(eventId);

        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, price));
        eventManagement.addStandingTickets(eventId, areaId, capacity);

        UUID lotteryId = UUID.randomUUID();

        PuchaseLottery lottery = new PuchaseLottery(
                lotteryId,
                eventId,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(14)
        );

        event.setLotteryId(lotteryId.toString());
        eventRepository.save(event);
        lotteryRepository.save(lottery);
    }
}
