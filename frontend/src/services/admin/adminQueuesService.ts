import api from "../api";
import type { QueueInfo } from "../../types/admin";

type AdminQueueSnapshotDto = {
    eventId: string;
    eventName?: string;
    queueSize: number;
    activeSelectorsCount: number;
    maxConcurrentSelectors: number;
    minutesToStartSelection: number;
};

function mapQueue(dto: AdminQueueSnapshotDto): QueueInfo {
    return {
        id: dto.eventId,
        eventName: dto.eventName || dto.eventId,
        waitingUsers: dto.queueSize,
        flowRatePerMinute: dto.maxConcurrentSelectors,
        activeSelectorsCount: dto.activeSelectorsCount,
        status:
            dto.queueSize === 0 && dto.activeSelectorsCount === 0
                ? "cleared"
                : "active",
    };
}

function extractMessage(error: unknown, fallback: string): string {
    if (
        typeof error === "object" &&
        error !== null &&
        "response" in error
    ) {
        const response = (error as { response?: { data?: { message?: string } } })
            .response;

        const message = response?.data?.message;

        if (typeof message === "string" && message.length > 0) {
            return message;
        }
    }

    if (error instanceof Error && error.message) {
        return error.message;
    }

    return fallback;
}

export async function getActiveQueues(_userId: string): Promise<QueueInfo[]> {
    try {
        const response = await api.get("/admin/queues");
        const queues = response.data.data as AdminQueueSnapshotDto[];

        return queues.map(mapQueue);
    } catch (error) {
        throw new Error(extractMessage(error, "Failed to fetch queues."), {
            cause: error,
        });
    }
}

export async function getQueue(
    _userId: string,
    queueId: string,
): Promise<QueueInfo> {
    try {
        const response = await api.get(
            `/admin/queues/${encodeURIComponent(queueId)}`,
        );

        return mapQueue(response.data.data as AdminQueueSnapshotDto);
    } catch (error) {
        throw new Error(extractMessage(error, "Failed to fetch queue."), {
            cause: error,
        });
    }
}

export async function releaseQueueBatch(
    _userId: string,
    queueId: string,
    batchSize: number,
): Promise<QueueInfo> {
    try {
        const response = await api.post(
            `/admin/queues/${encodeURIComponent(queueId)}/release`,
            { batchSize },
        );

        return mapQueue(response.data.data as AdminQueueSnapshotDto);
    } catch (error) {
        throw new Error(
            extractMessage(error, "Failed to release queue batch."),
            { cause: error },
        );
    }
}

export async function updateQueueFlowRate(
    _userId: string,
    _queueId: string,
    flowRatePerMinute: number,
): Promise<void> {
    try {
        await api.patch("/admin/queues/settings", {
            maxConcurrentSelectors: flowRatePerMinute,
        });
    } catch (error) {
        throw new Error(
            extractMessage(error, "Failed to update queue settings."),
            { cause: error },
        );
    }
}

export async function updateQueueSelectionWindow(
    _userId: string,
    minutesToStartSelection: number,
): Promise<void> {
    try {
        await api.patch("/admin/queues/settings", {
            minutesToStartSelection,
        });
    } catch (error) {
        throw new Error(
            extractMessage(error, "Failed to update queue selection window."),
            { cause: error },
        );
    }
}



export async function clearQueue(
    _userId: string,
    queueId: string,
): Promise<QueueInfo> {
    try {
        const response = await api.delete(
            `/admin/queues/${encodeURIComponent(queueId)}`,
        );

        return mapQueue(response.data.data as AdminQueueSnapshotDto);
    } catch (error) {
        throw new Error(extractMessage(error, "Failed to clear queue."), {
            cause: error,
        });
    }
}