package org.example.DomainLayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;

public interface ILotteryRepository {
    void save(PuchaseLottery lottery);
    PuchaseLottery findByID(UUID lotteryId);
    PuchaseLottery findByEventID(UUID eventId);
    List<PuchaseLottery> findAll();
    List<UUID> findEventIdsReadyForDraw(LocalDateTime now);
}
