package org.example.DomainLayer.LotteryAggregate;

import org.example.DomainLayer.DomainException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PuchaseLottery {

    private final String lotteryId;
    private final String eventId;
    private final LocalDateTime registrationOpen;
    private final LocalDateTime registrationClose;
    private final Set<String> registeredUsers;
    private final Set<String> winnerUsers;
    // add accessor for winnerUsers if needed--------------------
    private final Map<String, String> winnerAccessCodes;
    private final Map<String, LocalDateTime> winnerCodeExpiry;

    
    public PuchaseLottery(String lotteryId,
                          String eventId,
                          LocalDateTime registrationOpen,
                          LocalDateTime registrationClose) {
        if (lotteryId == null || lotteryId.isBlank()) {
            throw new DomainException("Lottery id cannot be empty");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new DomainException("Event id cannot be empty");
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
        this.winnerUsers = new HashSet<>();
        this.winnerAccessCodes = new HashMap<>();
        this.winnerCodeExpiry = new HashMap<>();
    }

    public String getLotteryId() {
        return lotteryId;
    }

    public String getEventId() {
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

    public void registerMember(String memberId, LocalDateTime now) {
        if (memberId == null || memberId.isBlank()) {
            throw new DomainException("Member id cannot be empty");
        }

        if (!isRegistrationOpen(now)) {
            throw new DomainException("Lottery registration is closed");
        }

        if (registeredUsers.contains(memberId)) {
            throw new DomainException("Member is already registered to this lottery");
        }

        registeredUsers.add(memberId);
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
}