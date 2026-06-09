package org.example.API;

import org.example.ApplicationLayer.ActivePurchaseCleaner;
import org.example.ApplicationLayer.EventPublisher;
import org.example.ApplicationLayer.IActiveSessionRegistry;
import org.example.ApplicationLayer.IAuthenticationGateway;
import org.example.ApplicationLayer.IKeyedLock;
import org.example.ApplicationLayer.ILoginRateLimiter;
import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.ISystemMetricsTracker;
import org.example.ApplicationLayer.ITicketingGateway;
import org.example.ApplicationLayer.ITokenBlacklist;
import org.example.ApplicationLayer.JwtService;
import org.example.ApplicationLayer.LotteryScheduler;
import org.example.ApplicationLayer.PurchaseService;
import org.example.ApplicationLayer.QueueManager;
import org.example.ApplicationLayer.SystemMetricsCollector;
import org.example.DomainLayer.*;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.InfrastructureLayer.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring wiring for the domain + infrastructure layers.
 *
 * The application layer ({@code EventService}, {@code CompanyService}, …)
 * is already annotated with {@code @Service}, so Spring's component scan
 * picks those up automatically. The domain layer and the infrastructure
 * implementations are deliberately framework-free, so we expose them as
 * {@code @Bean}s here — one method per object, simple constructor calls,
 * no magic.
 */
@Configuration
public class BeanConfig {

    private final BackendConfigProperties backendConfigProperties;

    public BeanConfig(BackendConfigProperties backendConfigProperties) {
        this.backendConfigProperties = backendConfigProperties;
    }

    // ---------------------------------------------------------------------
    // Repositories (in-memory implementations for the dev profile).
    // ---------------------------------------------------------------------

    @Bean
    public IEventRepository eventRepository() {
        return new InMemoryEventRepository();
    }

    @Bean
    @Profile("!localdb")
    public ICompanyRepository companyRepository() {
        return new CompanyRepository();
    }


    @Bean
    public IHistoryRepository historyRepository() {
        return new HistoryRepository();
    }

    @Bean
    public ILotteryRepository lotteryRepository() {
        return new LotteryRepository();
    }

    @Bean
    public IPurchaseRepository purchaseRepository() {
        return new InMemoryPurchaseRepository();
    }

    @Bean
    @Profile("!localdb")
    public IUserRepository userRepository() {
        return new UserRepository();
    }

    @Bean
    @Profile("!localdb")
    public IAdminRepository adminRepository() {
        return new AdminRepository();
    }

    // ---------------------------------------------------------------------
    // Gateways / adapters — dev stubs so the context boots and the
    // purchase / auth flows can be exercised locally.
    // ---------------------------------------------------------------------

    /**
     * Payment + ticketing gateways are exposed by their *concrete* stub
     * types so {@code DevStubController} can flip outcomes via the
     * setters on the stubs. The concrete classes implement
     * {@code IPaymentGateway} / {@code ITicketingGateway}, so anywhere
     * the domain layer asks for the interface, Spring injects the same
     * stub bean by type — no need for a separate interface-typed bean.
     *
     * We deliberately avoid declaring a second {@code @Bean} of type
     * {@code IPaymentGateway} (or {@code ITicketingGateway}) here: that
     * would create two beans assignable to the same interface, and
     * Spring 6.1+ no longer falls back to parameter-name matching at
     * runtime when the project is compiled without {@code -parameters},
     * which would break {@link #purchaseDomainService}'s autowiring.
     */
    @Bean
    public SimulatedPaymentGateway simulatedPaymentGateway() {
        return new SimulatedPaymentGateway();
    }

    @Bean
    public SimulatedTicketingGateway simulatedTicketingGateway() {
        return new SimulatedTicketingGateway();
    }


    @Bean
    public IAuthenticationGateway authenticationGateway() {
        return new BCryptAuthenticationGateway();
    }

    @Bean
    public ITokenBlacklist tokenBlacklist() {
        return new InMemoryTokenBlacklist();
    }

    /**
     * Per-key mutual exclusion for the identity lifecycle (register / login /
     * logout). Keyed on the normalised email string for register and login,
     * and on the user UUID string for logout.
     *
     * <p>Swap this for a Redis-backed {@link IKeyedLock} implementation when
     * the service is deployed across multiple JVM instances.
     */
    @Bean
    public IKeyedLock<String> userKeyedLock() {
        return new InMemoryKeyedLock<>();
    }

    /**
     * Per-account sliding-window rate limiter for the login endpoint.
     *
     * <p>Allows at most 5 failed attempts within a 15-minute window before
     * throwing {@link org.example.ApplicationLayer.RateLimitExceededException}.
     * Swap for a Redis-backed implementation in a multi-JVM deployment.
     */
    @Bean
    public ILoginRateLimiter loginRateLimiter() {
        BackendConfigProperties.LoginRateLimiter config = backendConfigProperties.getLoginRateLimiter();
        return new InMemoryLoginRateLimiter(config.getMaxFailedAttempts(), config.getWindow());
    }

    /**
     * Single-session registry — enforces at-most-one live JWT per user.
     *
     * <p>{@link InMemorySessionRegistry} uses {@link java.util.concurrent.ConcurrentHashMap#put}
     * for an atomic swap; no external synchronization is required.
     * Replace with a Redis-backed implementation for horizontal scaling.
     */
    @Bean
    public IActiveSessionRegistry activeSessionRegistry() {
        return new InMemorySessionRegistry();
    }

    @Bean
    public Broadcaster broadcaster() {
        return new Broadcaster();
    }

    @Bean
    public INotificationRepository notificationRepository() {
        return new NotificationRepository();
    }

    @Bean
    public INotifier notifier(Broadcaster broadcaster,
                              INotificationRepository notificationRepository) {
        return new Notifier(broadcaster, notificationRepository);
    }

    // ---------------------------------------------------------------------
    // Domain services — pure POJOs that Spring injects with the repos above.
    // ---------------------------------------------------------------------

    @Bean
    public EventManagementDomainService eventManagementDomainService(
            IEventRepository eventRepository,
            IHistoryRepository historyRepository,
            ICompanyRepository companyRepository,
            IUserRepository userRepository,
            ILotteryRepository lotteryRepository) {
        return new EventManagementDomainService(
                eventRepository, historyRepository, companyRepository, userRepository, lotteryRepository);
    }

    @Bean
    public RolesDomainService rolesDomainService(
            ICompanyRepository companyRepository,
            IUserRepository userRepository) {
        return new RolesDomainService(companyRepository, userRepository);
    }

    @Bean
    public PurchaseDomainService purchaseDomainService(
            IHistoryRepository historyRepository,
            IEventRepository eventRepository,
            IPurchaseRepository purchaseRepository,
            ICompanyRepository companyRepository,
            IUserRepository userRepository,
            ILotteryRepository lotteryRepository,
            IPaymentGateway paymentGateway,
            ITicketingGateway ticketingGateway) {
        return new PurchaseDomainService(
                historyRepository, eventRepository, purchaseRepository,
                companyRepository, userRepository, lotteryRepository,
                paymentGateway, ticketingGateway, backendConfigProperties);
    }

    // ---------------------------------------------------------------------
    // Application-layer helpers used by PurchaseService.
    // ---------------------------------------------------------------------

    @Bean
    public EventPublisher eventPublisher() {
        return new EventPublisher();
    }

    @Bean
    public QueueManager queueManager(INotifier notifier) {
        return new QueueManager(notifier);
    }

    // ---------------------------------------------------------------------
    // System Analytics — sliding-window rate tracker + event collector.
    //
    // InMemorySystemMetricsTracker is the Infrastructure adapter that
    // implements the ISystemMetricsTracker port.  SystemMetricsCollector is
    // the Application-layer handler that bridges domain events to the
    // tracker.  Both are framework-free (no @Service / @Component), so they
    // must be wired explicitly here.
    //
    // The collector is subscribed to the shared EventPublisher in the same
    // factory method that creates it, so no other class needs to know about
    // the subscription.
    // ---------------------------------------------------------------------

    @Bean
    public ISystemMetricsTracker systemMetricsTracker() {
        BackendConfigProperties.SystemMetrics config = backendConfigProperties.getSystemMetrics();
        return new InMemorySystemMetricsTracker(config.getWindow());
    }

    @Bean
    public SystemMetricsCollector systemMetricsCollector(
            ISystemMetricsTracker metricsTracker,
            EventPublisher eventPublisher) {
        SystemMetricsCollector collector = new SystemMetricsCollector(metricsTracker);
        eventPublisher.subscribe(collector::handle);
        return collector;
    }

    // ---------------------------------------------------------------------
    // Background sweep — releases tickets from reservations whose 10-minute
    // wait window has lapsed. Spring calls start() right after construction
    // (initMethod) and interrupt() on context shutdown (destroyMethod), so
    // the thread mirrors the application's lifecycle.
    // ---------------------------------------------------------------------

    @Bean(initMethod = "start", destroyMethod = "interrupt")
    public ActivePurchaseCleaner activePurchaseCleaner(
            PurchaseService purchaseService,
            IPurchaseRepository purchaseRepository,
            INotifier notifier) {
        BackendConfigProperties.ActivePurchaseCleaner config = backendConfigProperties.getActivePurchaseCleaner();
        return new ActivePurchaseCleaner(
            purchaseService,
            purchaseRepository,
            notifier,
            config.getSweepInterval(),
            config.getWarningBeforeExpirySeconds());
    }

    @Bean(initMethod = "start", destroyMethod = "interrupt")
    public LotteryScheduler lotteryScheduler(
            PurchaseDomainService purchaseDomainService,
            ILotteryRepository lotteryRepository,
            INotifier notifier,
            IEventRepository eventRepository) {
        return new LotteryScheduler(purchaseDomainService, lotteryRepository, notifier, eventRepository);
    }

    // ---------------------------------------------------------------------
    // Web layer — JWT authentication filter.
    //
    // {@link JwtAuthFilter} reads the {@code Authorization: Bearer …}
    // header, validates the token via {@link JwtService}, and exposes
    // {@code userId} / {@code username} / {@code role} as request
    // attributes that controllers (notably {@link AdminController}) read
    // back to identify the caller.
    //
    // The filter is framework-free — no {@code @Component} annotation —
    // so it must be wired explicitly here. Spring Boot auto-registers
    // any {@code Filter} bean as a servlet filter for {@code /*}; the
    // filter's own {@code shouldNotFilter} / {@code PUBLIC_PATHS}
    // handles the endpoints that must remain reachable without a token
    // (guest / login / register / logout), and a missing Authorization
    // header is a benign pass-through (so static resources and the
    // dev-stub controller aren't affected).
    // ---------------------------------------------------------------------

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService, backendConfigProperties);
    }
}
