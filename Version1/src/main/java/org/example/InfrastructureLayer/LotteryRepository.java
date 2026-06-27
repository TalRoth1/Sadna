package org.example.InfrastructureLayer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.example.DomainLayer.ILotteryRepository;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;

public class LotteryRepository implements ILotteryRepository {
    private final Map<UUID, PuchaseLottery> lotteriesById = new HashMap<>();
    private final Map<UUID, PuchaseLottery> lotteriesByEventId = new HashMap<>();

    @Override
    public void save(PuchaseLottery lottery) {
        lotteriesById.put(lottery.getLotteryId(), lottery);
        lotteriesByEventId.put(lottery.getEventId(), lottery);
    }

    @Override
    public PuchaseLottery findByID(UUID lotteryId) {
        return lotteriesById.get(lotteryId);
    }

    @Override
    public PuchaseLottery findByEventID(UUID eventId) {
        return lotteriesByEventId.get(eventId);
    }

    @Override
    public List<PuchaseLottery> findAll() {
        return new ArrayList<>(lotteriesById.values());
    }

    @Override
    public List<UUID> findEventIdsReadyForDraw(LocalDateTime now) {
        return lotteriesById.values()
                .stream()
                .filter(lottery -> lottery.getRegistrationClose() != null)
                .filter(lottery -> !lottery.getRegistrationClose().isAfter(now))
                .filter(lottery -> !lottery.getRegisteredUsers().isEmpty())
                .filter(lottery -> lottery.getWinnerUsers().isEmpty())
                .map(PuchaseLottery::getEventId)
                .toList();
    }
}
