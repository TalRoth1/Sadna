package org.example.API;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.StandingArea;
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
    private final EventManagementDomainService eventManagement;

    public DevDataSeeder(IEventRepository eventRepository,
                         ICompanyRepository companyRepository,
                         EventManagementDomainService eventManagement) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
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
}
