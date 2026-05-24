package org.example.API;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.IAdminRepository;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.ILotteryRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.NotificationAggregate.Notification;
import org.example.DomainLayer.NotificationAggregate.NotificationType;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.User;
import org.example.InfrastructureLayer.NotificationRepository;
import org.example.InfrastructureLayer.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a comprehensive in-memory dataset the first time the app boots
 * against an empty event repository, so the React UI and the rest of
 * the team can exercise every code path without standing up real
 * databases or running through the registration / company-creation /
 * event-publishing flows by hand.
 *
 * Wrapped in the {@code dev} profile (active by default — see
 * {@code application.properties}) so production builds don't
 * accidentally insert demo rows.
 *
 * The dataset deliberately includes edge cases alongside happy paths:
 * - A closed (AdminClose'd) company whose events must NOT appear in
 * public search.
 * - A canceled event that must NOT appear in public search either.
 * - A completely sold-out event (all tickets marked SOLD) so the
 * "no tickets available" UI can be exercised.
 * - An adult-only event with an AgeRule (≥ 18) so the policy
 * validation path is reachable.
 * - A composite (AND) and an alternative composite (OR) policy.
 * - An active OvertDiscount, an expired one, a future-dated one, a
 * ConditionalDiscount ("buy 3, 1 free"), and a CouponCode.
 * - A lottery event with one pre-registered winner who already
 * holds a valid access code.
 * - Pending owner + manager invitations (not yet accepted).
 * - Pre-existing purchase history rows for the sales-report demo.
 * - Pre-existing complaints in OPEN / ANSWERED / CLOSED states.
 * - Pre-seeded notifications for the bell-icon demo.
 * - A historical analytics snapshot so the admin analytics page has
 * something to compare the live snapshot against.
 *
 * The seed is idempotent: it short-circuits when the event repository
 * already contains data, so restarting the app does not duplicate
 * anything.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

        private static final Logger logger = Logger.getLogger(DevDataSeeder.class.getName());

        /** Shared password for every demo member — easy to type at login. */
        private static final String DEFAULT_PASSWORD = "demo1234";

        // -----------------------------------------------------------------
        // Repositories + services — constructor-injected. UserRepository is
        // injected as the concrete type (not IUserRepository) because the
        // `addAdmin(Admin)` method only exists on the concrete class.
        // -----------------------------------------------------------------
        private final IEventRepository eventRepository;
        private final ICompanyRepository companyRepository;
        private final ILotteryRepository lotteryRepository;
        private final IHistoryRepository historyRepository;
        private final UserRepository userRepository;
        private final IAdminRepository adminRepository;
        private final NotificationRepository notificationRepository;
        private final EventManagementDomainService eventManagement;
        private final RolesDomainService rolesDomainService;

        // -----------------------------------------------------------------
        // Cast registry — built as we seed, so later sections can refer to
        // demo identities by stable string keys instead of UUIDs.
        // -----------------------------------------------------------------
        private final Map<String, User> usersByEmail = new HashMap<>();
        private final Map<String, UUID> companiesByName = new HashMap<>();
        private final Map<String, UUID> eventsByKey = new HashMap<>();

        public DevDataSeeder(IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        ILotteryRepository lotteryRepository,
                        IHistoryRepository historyRepository,
                        IUserRepository userRepository,
                        IAdminRepository adminRepository,
                        NotificationRepository notificationRepository,
                        EventManagementDomainService eventManagement,
                        RolesDomainService rolesDomainService) {
                this.eventRepository = eventRepository;
                this.companyRepository = companyRepository;
                this.lotteryRepository = lotteryRepository;
                this.historyRepository = historyRepository;
                // The cast is safe here: BeanConfig wires UserRepository as the
                // IUserRepository bean, and addAdmin(...) lives on the concrete
                // class. Documented so a future reader doesn't try to "fix" it.
                this.userRepository = (UserRepository) userRepository;
                this.adminRepository = adminRepository;
                this.notificationRepository = notificationRepository;
                this.eventManagement = eventManagement;
                this.rolesDomainService = rolesDomainService;
        }

        @Override
        public void run(String... args) {
                if (!eventRepository.getAll().isEmpty()) {
                        logger.info("[DevDataSeeder] repository already has events — skipping seed.");
                        return;
                }

                seedUsers();
                seedAdmin();
                seedCompanies();
                seedCompanyRoles();
                seedPendingInvitations();
                seedEvents();
                attachEventPolicies();
                seedLottery();
                closeClosedCompany();
                seedPurchaseHistory();
                seedComplaints();
                seedNotifications();
                seedAnalytics();

                logger.log(Level.INFO,
                                "[DevDataSeeder] seeded {0} events, {1} users, {2} active companies, and {3} purchase-history rows.",
                                new Object[] {
                                                eventRepository.getAll().size(),
                                                userRepository.getAllUsers().size(),
                                                companyRepository.getAllActive().size(),
                                                historyRepository.getAll().size()
                                });
        }

        /**
         * Attach a standing (general-admission) area with a flat pool of
         * tickets at the given price.
         */
        // =================================================================
        // SECTION 1: Users
        //
        // 23 demo members in total. One of them (`admin@demo.test`) gets
        // the system-admin role wired up in seedAdmin() — per spec, an
        // admin must also be a registered member ("מנהל-מערכת חייב להיות
        // מנוי"). Emails follow a `firstname@demo.test` pattern so they're
        // predictable in integration tests.
        // =================================================================
        private void seedUsers() {
                registerMember("admin@demo.test", "admin", 35);
                registerMember("founder.live@demo.test", "founder-live", 40);
                registerMember("owner.live@demo.test", "owner-live", 38);
                registerMember("manager.live.inv@demo.test", "manager-live-inv", 30);
                registerMember("manager.live.full@demo.test", "manager-live-full", 32);
                registerMember("pending.owner@demo.test", "pending-owner", 29);
                registerMember("pending.manager@demo.test", "pending-manager", 26);
                registerMember("founder.indie@demo.test", "founder-indie", 45);
                registerMember("founder.closed@demo.test", "founder-closed", 50);
                registerMember("alice@demo.test", "alice", 28);
                registerMember("bob@demo.test", "bob", 24);
                registerMember("carol@demo.test", "carol", 22);
                registerMember("dave@demo.test", "dave", 19);
                registerMember("minor@demo.test", "minor", 16);
                
                // Extra cast for a deeper hierarchy graph in Mega Events Group.
                registerMember("founder.mega@demo.test", "founder-mega", 41);
                registerMember("owner.mega.alpha@demo.test", "owner-mega-alpha", 36);
                registerMember("owner.mega.beta@demo.test", "owner-mega-beta", 37);
                registerMember("manager.mega.ops@demo.test", "manager-mega-ops", 34);
                registerMember("manager.mega.sales@demo.test", "manager-mega-sales", 33);
                registerMember("manager.mega.support@demo.test", "manager-mega-support", 31);
                registerMember("manager.mega.finance@demo.test", "manager-mega-finance", 39);
                registerMember("pending.mega.owner@demo.test", "pending-mega-owner", 28);
                registerMember("pending.mega.manager@demo.test", "pending-mega-manager", 27);
        }

        // =================================================================
        // SECTION 2: System Admin
        //
        // Spec ("יש למערכת לפחות מנהל-מערכת אחד") requires at least one admin.
        // The admin identity has two halves in this codebase:
        // 1. A regular User record (so they can log in via JWT).
        // 2. An Admin entry in UserRepository.addAdmin(...), keyed by id
        // and matched by username for isSystemAdmin(...) lookups.
        // We wire both for the admin.demo.test member so the admin endpoints
        // resolve correctly.
        // =================================================================
        private void seedAdmin() {
                User adminUser = usersByEmail.get("admin@demo.test");
                // The Admin interface has only getId/getUsername. We use an
                // anonymous implementation rather than introducing a concrete
                // class just for the seeder — this matches what the existing
                // tests do.
                Admin adminImpl = new Admin() {
                        @Override
                        public UUID getId() {
                                return adminUser.getId();
                        }

                        @Override
                        public String getUsername() {
                                return adminUser.getEmail();
                        }
                };
                userRepository.addAdmin(adminImpl);
        }

        // =================================================================
        // SECTION 3: Companies
        //
        // 4 companies covering the full lifecycle:
        // - Live Nation Demo: active, rich hierarchy.
        // - Indie Productions: active, founder-only.
        // - Closed Co.: closed after seeding (see closeClosedCompany).
        // =================================================================
        private void seedCompanies() {
                createCompany("founder.live@demo.test", "Live Nation Demo");
                createCompany("founder.indie@demo.test", "Indie Productions");
                createCompany("founder.closed@demo.test", "Closed Co.");
                createCompany("founder.mega@demo.test", "Mega Events Group");
        }

        // =================================================================
        // SECTION 4: Company role assignments
        //
        // The founder role is the trickiest: no service assigns it (the spec
        // says the act of opening a company makes you a founder, but
        // RolesDomainService.createCompany only persists the company record
        // — it doesn't grant the founder role on the user side). We mirror
        // what the existing unit tests do and assign CompanyFounder by
        // mutating `user.getCompanyRoles()` directly. This is the canonical
        // dev/test pattern in this codebase.
        //
        // Owner / manager assignments go through the regular invite + accept
        // flow on RolesDomainService so we exercise the real code paths.
        // =================================================================
        private void seedCompanyRoles() {
                UUID liveNationId = companiesByName.get("Live Nation Demo");
                UUID indieId = companiesByName.get("Indie Productions");
                UUID closedId = companiesByName.get("Closed Co.");
                UUID megaId = companiesByName.get("Mega Events Group");

                assignFounder("founder.live@demo.test", liveNationId);
                assignFounder("founder.indie@demo.test", indieId);
                assignFounder("founder.closed@demo.test", closedId);
                assignFounder("founder.mega@demo.test", megaId);

                // Live Nation gets a richer hierarchy:
                // founder -> owner.live (full owner rights)
                // founder -> manager.live.inv (MANAGE_INVENTORY only)
                // founder -> manager.live.full (every permission)
                inviteAndAcceptOwner("founder.live@demo.test", liveNationId,
                                "owner.live@demo.test");

                inviteAndAcceptManager("founder.live@demo.test", liveNationId,
                                "manager.live.inv@demo.test",
                                Set.of(CompanyPermission.MANAGE_INVENTORY));

                inviteAndAcceptManager("founder.live@demo.test", liveNationId,
                                "manager.live.full@demo.test",
                                Set.of(CompanyPermission.values()));

                // Mega Events Group hierarchy (deeper and multi-branch):
                // founder.mega
                // ├─ owner.mega.alpha
                // │ ├─ manager.mega.ops
                // │ └─ manager.mega.support
                // └─ owner.mega.beta
                // ├─ manager.mega.sales
                // └─ manager.mega.finance
                inviteAndAcceptOwner("founder.mega@demo.test", megaId,
                                "owner.mega.alpha@demo.test");

                inviteAndAcceptOwner("founder.mega@demo.test", megaId,
                                "owner.mega.beta@demo.test");

                inviteAndAcceptManager("owner.mega.alpha@demo.test", megaId,
                                "manager.mega.ops@demo.test",
                                Set.of(CompanyPermission.MANAGE_INVENTORY,
                                                CompanyPermission.VIEW_HISTORY,
                                                CompanyPermission.REPORTS_GENERATION));

                inviteAndAcceptManager("owner.mega.alpha@demo.test", megaId,
                                "manager.mega.support@demo.test",
                                Set.of(CompanyPermission.CUSTOMER_SERVICE,
                                                CompanyPermission.MANAGE_POLICIES));

                inviteAndAcceptManager("owner.mega.beta@demo.test", megaId,
                                "manager.mega.sales@demo.test",
                                Set.of(CompanyPermission.VIEW_HISTORY,
                                                CompanyPermission.REPORTS_GENERATION,
                                                CompanyPermission.CONFIGURE_LAYOUT));

                inviteAndAcceptManager("owner.mega.beta@demo.test", megaId,
                                "manager.mega.finance@demo.test",
                                Set.of(CompanyPermission.VIEW_HISTORY,
                                                CompanyPermission.REPORTS_GENERATION));
        }

        // =================================================================
        // SECTION 5: Pending invitations
        //
        // Two invitations that have NOT been accepted, so the "pending
        // invitations" UI has something to render and the accept/reject
        // flows can be smoke-tested manually.
        // =================================================================
        private void seedPendingInvitations() {
                UUID liveNationId = companiesByName.get("Live Nation Demo");
                UUID megaId = companiesByName.get("Mega Events Group");
                rolesDomainService.inviteCompanyOwner(
                                "founder.live@demo.test", liveNationId,
                                "pending.owner@demo.test");

                rolesDomainService.inviteCompanyManager(
                                "founder.live@demo.test", liveNationId,
                                "pending.manager@demo.test",
                                Set.of(CompanyPermission.MANAGE_INVENTORY,
                                                CompanyPermission.MANAGE_POLICIES));

                // Pending branches for hierarchy testing in Mega Events Group.
                rolesDomainService.inviteCompanyOwner(
                                "founder.mega@demo.test", megaId,
                                "pending.mega.owner@demo.test");

                rolesDomainService.inviteCompanyManager(
                                "owner.mega.alpha@demo.test", megaId,
                                "pending.mega.manager@demo.test",
                                Set.of(CompanyPermission.MANAGE_INVENTORY,
                                                CompanyPermission.CUSTOMER_SERVICE));
        }

        // =================================================================
        // SECTION 6: Events
        //
        // 10 events covering every shape the catalog/search/details pages
        // need to render. Each event is created via EventManagementDomainService
        // (the same path the real flows use) and has its areas + tickets
        // attached through the canonical addStandingArea / addSittingArea
        // helpers.
        // =================================================================
        private void seedEvents() {
                UUID liveNationId = companiesByName.get("Live Nation Demo");
                UUID indieId = companiesByName.get("Indie Productions");
                UUID closedId = companiesByName.get("Closed Co.");

                // 1. Coldplay — mixed standing + sitting, large concert.
                UUID coldplay = createEvent("coldplay", liveNationId, "founder.live@demo.test",
                                "Coldplay – Music of the Spheres", "Coldplay",
                                "Concert", "Tel Aviv",
                                LocalDateTime.now().plusDays(30), EventStatus.ACTIVE);
                addStandingArea(coldplay, 350.0, 200);
                addSittingArea(coldplay, 600.0, 8, 10);

                // 2. Hapoel vs Maccabi — pure sitting, stadium fixture.
                UUID hapoel = createEvent("hapoel", liveNationId, "founder.live@demo.test",
                                "Hapoel TLV vs Maccabi", "Hapoel Tel Aviv",
                                "Sports", "Bloomfield Stadium, Tel Aviv",
                                LocalDateTime.now().plusDays(7), EventStatus.ACTIVE);
                addSittingArea(hapoel, 120.0, 12, 20);

                // 3. Adir Miller — small theatre, pure sitting.
                UUID adirMiller = createEvent("adir-miller", indieId, "founder.indie@demo.test",
                                "Stand-up Night with Adir Miller", "Adir Miller",
                                "Comedy", "Habima Theatre, Tel Aviv",
                                LocalDateTime.now().plusDays(14), EventStatus.ACTIVE);
                addSittingArea(adirMiller, 180.0, 6, 12);

                // 4. Jazz at the Cellar — intimate club, pure standing.
                UUID jazz = createEvent("jazz", indieId, "founder.indie@demo.test",
                                "Jazz at the Cellar", "Avishai Cohen Trio",
                                "Jazz", "Beit Haamudim, Tel Aviv",
                                LocalDateTime.now().plusDays(21), EventStatus.ACTIVE);
                addStandingArea(jazz, 220.0, 60);

                // 5. Adults Only — used for the AgeRule edge case (≥ 18).
                UUID adultsOnly = createEvent("adults-only", liveNationId, "founder.live@demo.test",
                                "Adults Only – Late Night Comedy", "Various",
                                "Comedy", "Zappa Club, Tel Aviv",
                                LocalDateTime.now().plusDays(10), EventStatus.ACTIVE);
                addStandingArea(adultsOnly, 250.0, 40);

                // 6. Taylor Swift — lottery-gated event. Lottery wired up in
                // seedLottery() so we can pre-register a winner.
                UUID taylor = createEvent("taylor-swift", liveNationId, "founder.live@demo.test",
                                "Taylor Swift – Lottery Night", "Taylor Swift",
                                "Concert", "Park Hayarkon, Tel Aviv",
                                LocalDateTime.now().plusDays(45), EventStatus.ACTIVE);
                addStandingArea(taylor, 450.0, 100);

                // 7. Tech Conference — composite OR policy + future discount.
                UUID techConf = createEvent("tech-conf", liveNationId, "founder.live@demo.test",
                                "Tech Conference 2026", "Various Speakers",
                                "Conference", "Expo Tel Aviv",
                                LocalDateTime.now().plusDays(60), EventStatus.ACTIVE);
                addStandingArea(techConf, 900.0, 50);

                // 8. Eurovision Watch Party — completely sold out. We mark every
                // ticket SOLD inline so the catalog renders a "sold out"
                // state without anyone running checkout.
                UUID eurovision = createEvent("eurovision", liveNationId, "founder.live@demo.test",
                                "Eurovision Watch Party", "Eurovision",
                                "Live Watch", "Tel Aviv",
                                LocalDateTime.now().plusDays(50), EventStatus.ACTIVE);
                addStandingArea(eurovision, 80.0, 30);
                markAllTicketsSold(eurovision);

                // 9. Beyoncé — canceled. Must NOT appear in public search.
                UUID beyonce = createEvent("beyonce", liveNationId, "founder.live@demo.test",
                                "Beyoncé – Renaissance Tour", "Beyoncé",
                                "Concert", "Tel Aviv",
                                LocalDateTime.now().plusDays(40), EventStatus.CANCELED);
                addStandingArea(beyonce, 500.0, 100);

                // 10. Forgotten Festival — under Closed Co., which gets closed
                // in closeClosedCompany() below. Even though the event is
                // ACTIVE itself, the closed-company filter must hide it.
                UUID forgottenFest = createEvent("forgotten-fest", closedId, "founder.closed@demo.test",
                                "Forgotten Festival 2020", "Various",
                                "Festival", "Caesarea Amphitheatre",
                                LocalDateTime.now().plusDays(90), EventStatus.ACTIVE);
                addStandingArea(forgottenFest, 150.0, 80);
        }

        // =================================================================
        // SECTION 7: Event-level purchase + discount policies
        //
        // We attach policies directly on the Event aggregate rather than
        // going through EventManagementDomainService.addPurchasePolicy(...)
        // / addOvertDiscount(...) etc. — those methods require a User in a
        // permission-bearing company role, which means injecting a logged-in
        // identity into a CommandLineRunner. Since the resulting object
        // state is identical, going through Event.addPurchasePolicy(...) is
        // the simpler, well-understood path.
        // =================================================================
        private void attachEventPolicies() {
                Event coldplay = eventRepository.getById(eventsByKey.get("coldplay"));
                Event hapoel = eventRepository.getById(eventsByKey.get("hapoel"));
                Event adirMiller = eventRepository.getById(eventsByKey.get("adir-miller"));
                Event jazz = eventRepository.getById(eventsByKey.get("jazz"));
                Event adultsOnly = eventRepository.getById(eventsByKey.get("adults-only"));
                Event taylor = eventRepository.getById(eventsByKey.get("taylor-swift"));
                Event techConf = eventRepository.getById(eventsByKey.get("tech-conf"));

                LocalDate today = LocalDate.now();

                // Coldplay: 15% Early Bird discount, ACTIVE right now.
                coldplay.addOvertDiscount(today.minusDays(7), today.plusDays(14), 15f);

                // Hapoel: anti-scalping. Max 4 tickets AND no lone-seat
                // patterns. Composite AND tree.
                hapoel.addPurchasePolicy(
                                Optional.empty(), // age — no restriction
                                Optional.empty(), // minTicket
                                Optional.of(4), // maxTicket
                                Optional.of(false), // allowLoneSeat=false → block lone seats
                                true); // AND-combine

                // Adir Miller: "buy 3 get 1 free" conditional discount.
                adirMiller.addConditionalDiscount(today.minusDays(1), today.plusDays(60),
                                100f, 3, 1);

                // Jazz: coupon code "JAZZ20" (20% off, active) AND an expired
                // OvertDiscount so the UI can demonstrate "expired" badges /
                // skip-logic.
                jazz.addCouponCode(today.minusDays(2), today.plusDays(30), 20f, "JAZZ20");
                jazz.addOvertDiscount(today.minusMonths(2), today.minusMonths(1), 30f);

                // Adults Only: age rule. `minor@demo.test` (16) should fail,
                // `dave@demo.test` (19) should pass.
                adultsOnly.addPurchasePolicy(
                                Optional.of(18f), // age ≥ 18
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                true);

                // Taylor Swift: lottery cap of 2 tickets per buyer (separate
                // from the lottery gating itself — that's enforced in
                // PurchaseDomainService.registerToLottery).
                taylor.addPurchasePolicy(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(2),
                                Optional.empty(),
                                true);

                // Tech Conference: composite OR — either at least 1 ticket
                // OR at most 10 tickets. (Trivial when combined; the point is
                // to demonstrate that OR composition serialises correctly for
                // the policy-builder UI.)
                techConf.addPurchasePolicy(
                                Optional.empty(),
                                Optional.of(1),
                                Optional.empty(),
                                Optional.empty(),
                                true); // first rule attaches alone
                techConf.addPurchasePolicy(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(10),
                                Optional.empty(),
                                false); // OR-combine with first rule
                // Tech Conference also gets a future-scheduled discount so the
                // "scheduled / not yet active" state is reachable.
                techConf.addOvertDiscount(today.plusDays(7), today.plusDays(45), 25f);

                // Persist every event we mutated so the in-memory repository
                // sees the policy attachments.
                for (Event e : new Event[] { coldplay, hapoel, adirMiller, jazz,
                                adultsOnly, taylor, techConf }) {
                        eventRepository.save(e);
                }
        }

        // =================================================================
        // SECTION 8: Lottery
        //
        // Taylor Swift event is lottery-gated. We:
        // 1. Create the PuchaseLottery aggregate with a 14-day open
        // registration window starting yesterday.
        // 2. Pre-register `bob@demo.test`, mark him a winner, generate a
        // 24-hour access code so the "I won" path is immediately
        // testable without running drawWinners(...).
        // 3. Stash the lotteryId on the Event so the EventDetails UI can
        // flag it as a lottery event.
        // =================================================================
        private void seedLottery() {
                UUID taylorEventId = eventsByKey.get("taylor-swift");
                UUID lotteryId = UUID.randomUUID();

                PuchaseLottery lottery = new PuchaseLottery(
                                lotteryId,
                                taylorEventId,
                                LocalDateTime.now().minusDays(1),
                                LocalDateTime.now().plusDays(14));

                User bob = usersByEmail.get("bob@demo.test");
                lottery.registerMember(bob.getId().toString(), 2, LocalDateTime.now());
                lottery.addWinner(bob.getId().toString());
                lottery.generateWinnerAccessCode(
                                bob.getId().toString(),
                                LocalDateTime.now().plusDays(1));

                lotteryRepository.save(lottery);

                Event taylor = eventRepository.getById(taylorEventId);
                taylor.setLotteryId(lotteryId.toString());
                eventRepository.save(taylor);
        }

        // =================================================================
        // SECTION 9: Close Closed Co.
        //
        // We close the company AFTER seeding its events, otherwise the
        // events under it wouldn't exist when the close happens. Closing
        // exercises the admin-close path on RolesDomainService.
        // =================================================================
        private void closeClosedCompany() {
                rolesDomainService.closeCompanyAsAdmin(
                                "admin@demo.test",
                                companiesByName.get("Closed Co."));
        }

        // =================================================================
        // SECTION 10: Purchase history
        //
        // Five historical purchase rows so the sales-report / personal
        // history UIs render without anyone running checkout. The Eurovision
        // event was already marked fully sold above; this section attaches
        // one history row to `alice@demo.test` for two of its tickets so
        // her "past purchases" list isn't empty.
        // =================================================================
        private void seedPurchaseHistory() {
                addHistoryRow("alice@demo.test", "eurovision", 2, "Eurovision sold-out share");
                addHistoryRow("alice@demo.test", "coldplay", 1, "Coldplay Early Bird");
                addHistoryRow("bob@demo.test", "adir-miller", 1, "Adir Miller single seat");
                addHistoryRow("dave@demo.test", "hapoel", 2, "Hapoel pair");
                addHistoryRow("carol@demo.test", "jazz", 1, "Jazz GA");
        }

        // =================================================================
        // SECTION 11: Complaints
        //
        // One in each AdminComplaintStatus so the admin complaint queue
        // shows variety from the first page load.
        // =================================================================
        private void seedComplaints() {
                User carol = usersByEmail.get("carol@demo.test");
                User alice = usersByEmail.get("alice@demo.test");

                // OPEN — needs admin attention.
                AdminComplaint openComplaint = new AdminComplaint(
                                carol.getId(), carol.getEmail(),
                                "Suspicious bot activity on Taylor Swift event",
                                "I noticed several accounts buying max-out tickets within seconds of opening.");
                adminRepository.saveComplaint(openComplaint);

                // ANSWERED — admin has responded but the user hasn't closed it.
                AdminComplaint answeredComplaint = new AdminComplaint(
                                carol.getId(), carol.getEmail(),
                                "Refund not received",
                                "I paid for tickets to a canceled event 3 weeks ago and haven't seen the refund yet.");
                answeredComplaint.respond("admin@demo.test",
                                "We've located your transaction and the refund will be processed within 5 business days.");
                adminRepository.saveComplaint(answeredComplaint);

                // CLOSED — fully resolved.
                AdminComplaint closedComplaint = new AdminComplaint(
                                alice.getId(), alice.getEmail(),
                                "Wrong seat allocated",
                                "I bought seat A12 but the ticket says A21. Mid-priority — already exchanged with another buyer.");
                closedComplaint.respond("admin@demo.test",
                                "Apologies for the confusion. We've corrected the assignment in our records.");
                closedComplaint.close();
                adminRepository.saveComplaint(closedComplaint);
        }

        // =================================================================
        // SECTION 12: Notifications
        //
        // Five demo notifications across different types so the bell icon
        // and notifications page have something interesting on the very
        // first login.
        // =================================================================
        private void seedNotifications() {
                addNotification("alice@demo.test", NotificationType.PURCHASE_COMPLETED,
                                "Your Eurovision Watch Party tickets are ready",
                                /* read= */false);

                addNotification("bob@demo.test", NotificationType.LOTTERY_WON,
                                "You won the Taylor Swift lottery — your access code is valid for 24 hours",
                                /* read= */false);

                addNotification("founder.live@demo.test", NotificationType.GENERAL,
                                "Welcome to Live Nation Demo's admin panel",
                                /* read= */true);

                addNotification("founder.closed@demo.test", NotificationType.COMPANY_CLOSED,
                                "Your company has been closed by an administrator",
                                /* read= */false);

                addNotification("manager.live.inv@demo.test", NotificationType.ROLE_CHANGED,
                                "Your manager permissions have been set",
                                /* read= */true);
        }

        // =================================================================
        // SECTION 13: Admin analytics snapshot
        //
        // One historical snapshot from "1 hour ago" so the analytics page
        // can render a comparison alongside the live snapshot. Numbers are
        // illustrative — the live snapshot reflects whatever the real
        // counters say at request time.
        // =================================================================
        private void seedAnalytics() {
                adminRepository.saveAnalyticsSnapshot(new SystemAnalyticsSnapshot(
                                /* registeredUsersCount= */ 14,
                                /* loggedInUsersCount= */ 2,
                                /* activeCompaniesCount= */ 2,
                                /* activeQueuesCount= */ 0,
                                /* activePurchasesCount= */ 0,
                                /* totalPurchasesCount= */ 5));
        }

        // =================================================================
        // Helpers
        // =================================================================

        /** Register a member user via direct repository insertion. */
        private User registerMember(String email, String username, float age) {
                UUID id = UUID.randomUUID();
                User user = new User(id, username, email, DEFAULT_PASSWORD, age);
                userRepository.add(user);
                usersByEmail.put(email, user);
                return user;
        }

        /**
         * Create a company via the repository and remember its id under
         * {@code companyName} so later sections can refer to it by name.
         */
        private void createCompany(String founderEmail, String companyName) {
                UUID companyId = companyRepository.createCompany(founderEmail, companyName);
                companiesByName.put(companyName, companyId);
        }

        /**
         * Assign {@link CompanyFounder} to a user by directly mutating the
         * companyRoles map. There's no service that does this — the spec
         * says opening a company makes you the founder, but the
         * implementation splits the two steps. Same pattern as in the
         * existing unit tests.
         */
        private void assignFounder(String founderEmail, UUID companyId) {
                User founder = usersByEmail.get(founderEmail);
                founder.getCompanyRoles().put(companyId, new CompanyFounder(founderEmail));
        }

        /**
         * Send an owner invitation and immediately accept it. Goes through
         * RolesDomainService both times, so the same code paths the live
         * UI hits are exercised.
         */
        private void inviteAndAcceptOwner(String appointerEmail, UUID companyId,
                        String appointeeEmail) {
                UUID invitationId = rolesDomainService.inviteCompanyOwner(
                                appointerEmail, companyId, appointeeEmail);
                rolesDomainService.acceptCompanyInvitation(
                                invitationId, appointeeEmail, companyId);
        }

        /**
         * Manager equivalent of {@link #inviteAndAcceptOwner} with the
         * desired permission set.
         */
        private void inviteAndAcceptManager(String appointerEmail, UUID companyId,
                        String appointeeEmail,
                        Set<CompanyPermission> permissions) {
                UUID invitationId = rolesDomainService.inviteCompanyManager(
                                appointerEmail, companyId, appointeeEmail, permissions);
                rolesDomainService.acceptCompanyInvitation(
                                invitationId, appointeeEmail, companyId);
        }

        /**
         * Create an ACTIVE event with no layout yet — callers attach the
         * areas they want via {@link #addStandingArea} / {@link #addSittingArea}.
         * Indexed under {@code key} so later sections can refer to it by name.
         */
        private UUID createEvent(String key, UUID companyId, String eventManagerEmail, String name,
                        String artist, String type, String location,
                        LocalDateTime date, EventStatus status) {
                UUID eventId = UUID.randomUUID();
                eventManagement.addEvent(eventId, companyId, eventManagerEmail, name, date, location,
                                artist, type, status);
                eventsByKey.put(key, eventId);
                return eventId;
        }

        /** Attach a standing (GA) area with a flat ticket pool. */
        private void addStandingArea(UUID eventId, double price, int capacity) {
                Event event = eventRepository.getById(eventId);
                UUID areaId = UUID.randomUUID();
                event.getLayout().addArea(new StandingArea(areaId, price));
                eventManagement.addStandingTickets(eventId, areaId, capacity);
        }

        /** Attach a seated area as a {@code rows × seatsPerRow} grid. */
        private void addSittingArea(UUID eventId, double price,
                        int rows, int seatsPerRow) {
                Event event = eventRepository.getById(eventId);
                UUID areaId = UUID.randomUUID();
                event.getLayout().addArea(new SittingArea(areaId, price));
                eventManagement.addSittingTickets(eventId, areaId, rows, seatsPerRow);
        }

        /**
         * Mark every ticket on the given event as SOLD. Ticket.markSold()
         * requires the ticket be RESERVED first, so we walk through the
         * state machine: AVAILABLE → reserve() → markSold(). This is only
         * acceptable in the seeder because we own the entire process; the
         * runtime path goes through PurchaseDomainService.
         */
        private void markAllTicketsSold(UUID eventId) {
                Event event = eventRepository.getById(eventId);
                Set<UUID> allTicketIds = new LinkedHashSet<>(event.getTicketsView().keySet());
                for (UUID tid : allTicketIds) {
                        Ticket ticket = event.getTicketsView().get(tid);
                        ticket.reserve();
                }
                event.sellTickets(allTicketIds);
                eventRepository.save(event);
        }

        /**
         * Add a single PurchaseHistory row for the given user / event /
         * ticket count. We pick the first {@code ticketCount} tickets we
         * find on the event regardless of their status, because the history
         * record is meant to look "frozen in time" — the spec is explicit
         * that purchase history is immutable to subsequent platform changes
         * (event cancellation, price changes, company closure). Total price
         * is computed from the picked tickets' face values.
         */
        private void addHistoryRow(String buyerEmail, String eventKey,
                        int ticketCount, String paymentInfo) {
                User buyer = usersByEmail.get(buyerEmail);
                UUID eventId = eventsByKey.get(eventKey);
                Event event = eventRepository.getById(eventId);

                List<UUID> ticketIds = new ArrayList<>();
                double total = 0.0;
                Iterator<Map.Entry<UUID, Ticket>> it = event.getTicketsView().entrySet().iterator();
                while (it.hasNext() && ticketIds.size() < ticketCount) {
                        Map.Entry<UUID, Ticket> entry = it.next();
                        ticketIds.add(entry.getKey());
                        total += entry.getValue().getPrice();
                }

                Payment payment = new Payment(total, paymentInfo);
                historyRepository.add(new PurchaseHistory(
                                buyer.getId(), ticketIds, eventId, payment));
        }

        /**
         * Save a Notification, optionally pre-flagging it as read. The
         * recipient id matches the convention used at runtime: the user's
         * UUID as a string (see {@code Notifier.notifyUser}).
         */
        private void addNotification(String recipientEmail, NotificationType type,
                        String message, boolean read) {
                User recipient = usersByEmail.get(recipientEmail);
                Notification notification = new Notification(
                                recipient.getId().toString(), type, message, /* targetUrl= */null);
                if (read) {
                        notification.markAsRead();
                }
                notificationRepository.save(notification);
        }

        /**
         * Attach a seated area as a {@code rows × seatsPerRow} grid; the
         * domain layer generates one {@code SittingTicket} per (row, seat)
         * pair, which is what the React schematic walks to render seats.
         */
}
