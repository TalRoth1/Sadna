package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.Member;
import org.example.DomainLayer.UserAggregate.User;

import java.util.ArrayList;
import java.util.List;

public class PurchaseDomainService {
    private final IHistoryRepository historyRepository;
    private final IEventRepository eventRepository;
    private final IPurchaseRepository purchaseRepository;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;



    public PurchaseDomainService(IHistoryRepository historyRepository,
                                 IEventRepository eventRepository,
                                 IPurchaseRepository purchaseRepository,
                                 ICompanyRepository companyRepository,
                                 IUserRepository userRepository) {
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.purchaseRepository = purchaseRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    public void addPurchaseToHistory(int userId, List<Integer> ticketIds, int eventId, Payment payment) {
        if (userId <= 0 || eventId <= 0 || ticketIds == null || ticketIds.isEmpty() || payment == null) {
            throw new IllegalArgumentException("Invalid purchase data");
        }
        PurchaseHistory purchaseHistory = new PurchaseHistory(userId, ticketIds, eventId, payment);
        historyRepository.add(purchaseHistory);
    }

    public List<PurchaseHistory> getAllHistory() {
        return historyRepository.getAll();
    }

    public List<PurchaseHistory> getHistoryByUser(int userId) {
        return historyRepository.getByUserId(userId);
    }

    public List<PurchaseHistory> getHistoryByEvent(int eventId) {
        return historyRepository.getByEventId(eventId);
    }

    public List<PurchaseHistory> getHistoryByCompany(int companyId) {
        List<PurchaseHistory> result = new ArrayList<>();

        for (PurchaseHistory history : historyRepository.getAll()) {
            Event event = eventRepository.getById(history.getEventId());

            if (event != null && event.getCompanyId() == companyId) {
                result.add(history);
            }
        }

        return result;
    }

    public List<PurchaseHistory> getPurchaseHistoryForMember(int userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user id");
        }

        User user = userRepository.getById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!(user instanceof Member)) {
            throw new IllegalArgumentException("User is not a member");
        }

        Member member = (Member) user;

        if (!member.isLoggedIn()) {
            throw new IllegalArgumentException("Member is not logged in");
        }

        return historyRepository.getByUserId(userId);
    }
}
