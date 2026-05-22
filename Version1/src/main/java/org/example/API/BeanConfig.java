package org.example.API;

import org.example.ApplicationLayer.EventPublisher;
import org.example.ApplicationLayer.IAuthenticationGateway;
import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.ITicketingGateway;
import org.example.ApplicationLayer.ITokenBlacklist;
import org.example.ApplicationLayer.QueueManager;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.ILotteryRepository;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.example.InfrastructureLayer.Broadcaster;
import org.example.InfrastructureLayer.CompanyRepository;
import org.example.InfrastructureLayer.HistoryRepository;
import org.example.InfrastructureLayer.InMemoryEventRepository;
import org.example.InfrastructureLayer.InMemoryPurchaseRepository;
import org.example.InfrastructureLayer.InMemoryTokenBlacklist;
import org.example.InfrastructureLayer.LotteryRepository;
import org.example.InfrastructureLayer.NoopPaymentGateway;
import org.example.InfrastructureLayer.NoopTicketingGateway;
import org.example.InfrastructureLayer.PlainTextAuthenticationGateway;
import org.example.InfrastructureLayer.UserRepository;
import org.example.InfrastructureLayer.WebSocketNotificationSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    // ---------------------------------------------------------------------
    // Repositories (in-memory implementations for the dev profile).
    // ---------------------------------------------------------------------

    @Bean
    public IEventRepository eventRepository() {
        return new InMemoryEventRepository();
    }

    @Bean
    public ICompanyRepository companyRepository() {
        return new CompanyRepository();
    }

    @Bean
    public IUserRepository userRepository() {
        return new UserRepository();
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

    // ---------------------------------------------------------------------
    // Gateways / adapters — dev stubs so the context boots and the
    // purchase / auth flows can be exercised locally.
    // ---------------------------------------------------------------------

    @Bean
    public IPaymentGateway paymentGateway() {
        return new NoopPaymentGateway();
    }

    @Bean
    public ITicketingGateway ticketingGateway() {
        return new NoopTicketingGateway();
    }

    @Bean
    public IAuthenticationGateway authenticationGateway() {
        return new PlainTextAuthenticationGateway();
    }

    @Bean
    public ITokenBlacklist tokenBlacklist() {
        return new InMemoryTokenBlacklist();
    }

    @Bean
    public Broadcaster broadcaster() {
        return new Broadcaster();
    }

    @Bean
    public INotifier notifier(Broadcaster broadcaster) {
        return new WebSocketNotificationSender(broadcaster);
    }

    // ---------------------------------------------------------------------
    // Domain services — pure POJOs that Spring injects with the repos above.
    // ---------------------------------------------------------------------

    @Bean
    public EventManagementDomainService eventManagementDomainService(
            IEventRepository eventRepository,
            IHistoryRepository historyRepository,
            ICompanyRepository companyRepository,
            IUserRepository userRepository) {
        return new EventManagementDomainService(
                eventRepository, historyRepository, companyRepository, userRepository);
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
            ILotteryRepository lotteryRepository) {
        return new PurchaseDomainService(
                historyRepository, eventRepository, purchaseRepository,
                companyRepository, userRepository, lotteryRepository);
    }

    // ---------------------------------------------------------------------
    // Application-layer helpers used by PurchaseService.
    // ---------------------------------------------------------------------

    @Bean
    public EventPublisher eventPublisher() {
        return new EventPublisher();
    }

    @Bean
    public QueueManager queueManager() {
        return new QueueManager();
    }
}
