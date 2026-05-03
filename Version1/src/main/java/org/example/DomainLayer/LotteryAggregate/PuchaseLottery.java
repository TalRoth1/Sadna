package org.example.DomainLayer.LotteryAggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.DomainException;

public class PuchaseLottery {

    private final UUID lotteryId;
    private final UUID eventId;
    private final LocalDateTime registrationOpen;
    private final LocalDateTime registrationClose;
    private final Set<String> registeredUsers;
    private final Map<String, Integer> requestedTicketAmounts;
    private final Set<String> winnerUsers;
    // add accessor for winnerUsers if needed--------------------
    private final Map<String, String> winnerAccessCodes;
    private final Map<String, LocalDateTime> winnerCodeExpiry;

    
    public PuchaseLottery(UUID lotteryId,
                          UUID eventId,
                          LocalDateTime registrationOpen,
                          LocalDateTime registrationClose) {
        if (lotteryId == null) {
            throw new DomainException("Lottery id cannot be null");
        }
        if (eventId == null) {
            throw new DomainException("Event id cannot be null");
        }
        if (registrationOpen == null || registrationClose == null) {
            throw new DomainException("Registration dates cannot be null");
        }
        if (registrationClose.isBefore(registrationOpen)) {
            throw new DomainException("Registration close time cannot be before open time");
        }

        this.lotteryId = lotteryId;
        this.eventId = eventId;
        this.registrationOpen = registrationOpen;
        this.registrationClose = registrationClose;
        this.registeredUsers = new HashSet<>();
        this.requestedTicketAmounts = new HashMap<>();
        this.winnerUsers = new HashSet<>();
        this.winnerAccessCodes = new HashMap<>();
        this.winnerCodeExpiry = new HashMap<>();
    }

    public UUID getLotteryId() {
        return lotteryId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public LocalDateTime getRegistrationOpen() {
        return registrationOpen;
    }

    public LocalDateTime getRegistrationClose() {
        return registrationClose;
    }

    public Set<String> getRegisteredUsers() {
        return Collections.unmodifiableSet(registeredUsers);
    }

    public boolean isRegistrationOpen(LocalDateTime now) {
        if (now == null) {
            throw new DomainException("Current time cannot be null");
        }

        return (now.isEqual(registrationOpen) || now.isAfter(registrationOpen)) &&
               (now.isEqual(registrationClose) || now.isBefore(registrationClose));
    }

    public boolean isRegistered(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new DomainException("Member id cannot be empty");
        }

        return registeredUsers.contains(memberId);
    }

    public void registerMember(String memberId,int ticketAmount, LocalDateTime now) {
        if (memberId == null || memberId.isBlank()) {
            throw new DomainException("Member id cannot be empty");
        }
        if (ticketAmount <= 0) {
            throw new DomainException("Ticket amount must be greater than zero");
        }

        if (!isRegistrationOpen(now)) {
            throw new DomainException("Lottery registration is closed");
        }

        if (registeredUsers.contains(memberId)) {
            throw new DomainException("Member is already registered to this lottery");
        }

        registeredUsers.add(memberId);
        requestedTicketAmounts.put(memberId, ticketAmount);
    }

    public int getRequestedTicketAmount(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new DomainException("Member id cannot be empty");
        }

        if (!registeredUsers.contains(memberId)) {
            throw new DomainException("Member is not registered to this lottery");
        }

        return requestedTicketAmounts.getOrDefault(memberId, 0);
    }


    public Map<String, Integer> getAllRequestedTicketAmounts() {
        return Collections.unmodifiableMap(requestedTicketAmounts);
    }

    public boolean isWinner(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new DomainException("Member id cannot be empty");
        }

        return winnerUsers.contains(memberId);
    }

    public void addWinner(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new DomainException("Member id cannot be empty");
        }

        if (!registeredUsers.contains(memberId)) {
            throw new DomainException("Member must be registered to be a winner");
        }

        winnerUsers.add(memberId);
    }

    public Set<String> getWinnerUsers() {
        return Collections.unmodifiableSet(winnerUsers);
    }

    // Add methods for managing winner access codes if needed--------------------
    public String getWinnerAccessCode(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new DomainException("Member id cannot be empty");
        }

        return winnerAccessCodes.get(memberId);
    }

    public String generateWinnerAccessCode(String memberId, LocalDateTime expiry) {
        if (memberId == null || memberId.isBlank()) {
            throw new DomainException("Member id cannot be empty");
        }
        if (expiry == null) {
            throw new DomainException("Expiry time cannot be null");
        }
        if (!winnerUsers.contains(memberId)) {
            throw new DomainException("Member must be a winner to generate an access code");
        }

        String accessCode = UUID.randomUUID().toString();
        winnerAccessCodes.put(memberId, accessCode);
        winnerCodeExpiry.put(memberId, expiry);
        return accessCode;
    }

    public boolean isAccessCodeValid(String memberId, String accessCode, LocalDateTime now) {
        if (memberId == null || memberId.isBlank()) {
            throw new DomainException("Member id cannot be empty");
        }
        if (accessCode == null || accessCode.isBlank()) {
            throw new DomainException("Access code cannot be empty");
        }
        if (now == null) {
            throw new DomainException("Current time can not be null");
        }

        String validCode = winnerAccessCodes.get(memberId);
        LocalDateTime expiry = winnerCodeExpiry.get(memberId);

        return accessCode.equals(validCode) && now.isBefore(expiry);
    }

    public void drawWinners(int availableTickets, LocalDateTime codeExpiry) {
        if (availableTickets <= 0) {
            throw new DomainException("No tickets available for lottery");
        }

        if (registeredUsers.isEmpty()) {
            throw new DomainException("No registered users to draw from");
        }

        if (codeExpiry == null) {
            throw new DomainException("Code expiry cannot be null");
        }

        if (!winnerUsers.isEmpty()) {
            throw new DomainException("Winners have already been drawn for this lottery");
        }

        List<String> candidates = new ArrayList<>(registeredUsers);
        Collections.shuffle(candidates);

        int remainingTickets = availableTickets;

        for (String memberId : candidates) {
            int requestedAmount = requestedTicketAmounts.get(memberId);

            if (requestedAmount <= remainingTickets) {
                winnerUsers.add(memberId);
                generateWinnerAccessCode(memberId, codeExpiry);
                remainingTickets -= requestedAmount;
            }

            if (remainingTickets == 0) {
                break;
            }
        }
    }
}