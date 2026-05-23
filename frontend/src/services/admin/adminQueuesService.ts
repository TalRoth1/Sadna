import api from "../api";
import type { QueueInfo } from "../../types/admin";

type AdminQueueSnapshotDto = {
    eventId: string;
    queueSize: number;
    activeSelectorsCount: number;
    maxConcurrentSelectors: number;
    minutesToStartSelection: number;
};

function mapQueue(dto: AdminQueueSnapshotDto): QueueInfo {
    return {
        id: dto.eventId,
        eventName: dto.eventId,
        waitingUsers: dto.queueSize,
        flowRatePerMinute: dto.maxConcurrentSelectors,
        activeSelectorsCount: dto.activeSelectorsCount,
        status: dto.queueSize === 0 && dto.activeSelectorsCount === 0 ? "cleared" : "active",
    };
}

export async function getActiveQueues(_userId: string): Promise<QueueInfo[]> {
    const response = await api.get("/admin/queues");
    const queues = response.data.data as AdminQueueSnapshotDto[];

    return queues.map(mapQueue);
}

export async function updateQueueFlowRate(
    _userId: string,
    _queueId: string,
    flowRatePerMinute: number,
): Promise<void> {
    await api.patch("/admin/queues/settings", {
        maxConcurrentSelectors: flowRatePerMinute,
    });
}

export async function clearQueue(
    _userId: string,
    queueId: string,
): Promise<void> {
    await api.delete(`/admin/queues/${queueId}`);
}