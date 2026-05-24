import api from "../api";
import type { Complaint } from "../../types/admin";

type AdminComplaintDto = {
    id: string;
    reporterUserId: string;
    reporterUsername: string;
    title: string;
    description: string;
    status: string;
    adminResponse?: string;
    responderAdminUsername?: string;
    createdAt?: string;
    respondedAt?: string;
};

function normalizeComplaintStatus(status: string): Complaint["status"] {
    const normalizedStatus = status.toLowerCase();

    if (
        normalizedStatus === "answered" ||
        normalizedStatus === "closed" ||
        normalizedStatus === "open"
    ) {
        return normalizedStatus;
    }

    return "open";
}

function mapComplaint(dto: AdminComplaintDto): Complaint {
    return {
        id: dto.id,
        title: dto.title,
        message: dto.description,
        reporterName: dto.reporterUsername,
        status: normalizeComplaintStatus(dto.status),
        adminResponse: dto.adminResponse,
        responderAdminUsername: dto.responderAdminUsername,
        createdAt: dto.createdAt,
        respondedAt: dto.respondedAt,
    };
}

export async function getComplaints(_userId: string): Promise<Complaint[]> {
    const response = await api.get("/admin/complaints");
    const complaints = response.data.data as AdminComplaintDto[];

    return complaints
        .map(mapComplaint)
        .filter((complaint) => complaint.status === "open");
}

export async function respondToComplaint(
    _userId: string,
    complaintId: string,
    responseText: string,
): Promise<void> {
    await api.patch(`/admin/complaints/${complaintId}/response`, {
        response: responseText,
    });
}

export async function sendSystemMessage(
    _userId: string,
    message: string,
): Promise<void> {
    await api.post("/admin/messages/system", {
        message,
    });
}