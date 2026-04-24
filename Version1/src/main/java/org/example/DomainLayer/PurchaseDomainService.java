package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class PurchaseDomainService {
    private final IHistoryRepository historyRepository;
    private final IEventRepository eventRepository;
    private final IPurchaseRepository purchaseRepository;
    private final ICompanyRepository companyRepository;



    public PurchaseDomainService(IHistoryRepository historyRepository,
                                 IEventRepository eventRepository,
                                 IPurchaseRepository purchaseRepository,
                                 ICompanyRepository companyRepository) {
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.purchaseRepository = purchaseRepository;
        this.companyRepository = companyRepository;
    }

    public void addPurchaseToHistory(int userId, List<Integer> ticketIds, int eventId, Payment payment) {
        if (ticketIds == null || payment == null) {
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


    public void updateActivePurchaseSittingTickets(String activePurchaseID, List<Integer> newTicketIDs)
    {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseID);
        if (activePurchase == null) {
            throw new DomainException("לא נמצאה הזמנה פעילה");
        }

        Event event = eventRepository.findByID(activePurchase.getEventID());

        synchronized (event)
        {
            if (activePurchase.isExpired(LocalDateTime.now())) {
                event.releaseTickets(activePurchase.getTicketIDs());
                purchaseRepository.deleteByID(activePurchaseID);
                throw new DomainException("פג תוקף ההזמנה הפעילה");
            }

            List<Integer> oldTicketIDs = activePurchase.getTicketIDs();

            try {
                event.releaseTickets(oldTicketIDs);
                event.reserveSittingTickets(newTicketIDs);

                LinkedHashMap<Integer, Double> newTicketPrices = new LinkedHashMap<>();
                for (int ticketId : newTicketIDs) {
                    newTicketPrices.put(ticketId, event.getTicket(ticketId).getPrice());
                }

                activePurchase.replaceTickets(newTicketPrices);
                purchaseRepository.save(activePurchase);
            }
            catch (DomainException | IllegalStateException e) {
                event.reserveSittingTickets(oldTicketIDs);
                throw e;
            }
        }
    }
    public void updateActivePurchaseStandingTickets(String activePurchaseId, int newAmount, int areaId)
    {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseID);
        if (activePurchase == null) {
            throw new DomainException("לא נמצאה הזמנה פעילה");
        }

        Event event = eventRepository.findByID(activePurchase.getEventID());

        synchronized (event)
        {
            if (activePurchase.isExpired(LocalDateTime.now())) {
                event.releaseTickets(activePurchase.getTicketIDs());
                purchaseRepository.deleteByID(activePurchaseID);
                throw new DomainException("פג תוקף ההזמנה הפעילה");
            }

            List<Integer> oldTicketIDs = activePurchase.getTicketIDs();

            try {
                event.releaseTickets(oldTicketIDs);
                List<Integer> newTicketIDs = event.reserveStandingTickets(newAmount, areaId);

                LinkedHashMap<Integer, Double> newTicketPrices = new LinkedHashMap<>();
                for (int ticketId : newTicketIDs) {
                    newTicketPrices.put(ticketId, event.getTicket(ticketId).getPrice());
                }

                activePurchase.replaceTickets(newTicketPrices);
                purchaseRepository.save(activePurchase);
            }
            catch (DomainException | IllegalStateException e) {
                event.reserveSittingTickets(oldTicketIDs);
                throw e;
            }
        }
    }


    public void cancelActivePurchase(String activePurchaseId)
    {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseID);
        if (activePurchase == null) {
            throw new DomainException("לא נמצאה הזמנה פעילה");
        }

        Event event = eventRepository.findByID(activePurchase.getEventID());

        synchronized (event)
        {
            event.releaseTickets(activePurchase.getTicketIDs());
            purchaseRepository.deleteByID(activePurchaseId);
        }
    }

    public ActivePurchase viewActivePurchase(String activePurchaseId)
    {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseID);
        if (activePurchase == null) {
            throw new DomainException("לא נמצאה הזמנה פעילה");
        }

        Event event = eventRepository.findByID(activePurchase.getEventID());

        synchronized (event)
        {
            if (activePurchase.isExpired(LocalDateTime.now()))
            {
                event.releaseTickets(activePurchase.getTicketIDs());
                purchaseRepository.deleteByID(activePurchaseId);
                throw new DomainException("פג תוקף ההזמנה שברצוננו לצפות");
            }
            else return activePurchase;
        }
    }


}
