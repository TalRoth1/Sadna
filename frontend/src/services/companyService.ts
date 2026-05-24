import api from "./api";
import type { EventSummary } from "../types/event";

export type CreateCompanyRequest = {
	founderEmail: string;
	companyName: string;
};

export type CompanyMembership = {
	companyId: string;
	companyName: string;
	role: string;
	status: string;
};

export type CompanyResponse = {
	id: string;
	name: string;
	founderEmail: string;
	rating: number;
	isActive: boolean;
	eventIds: string[];
};

export type CompanyPermissionName =
	| "MANAGE_INVENTORY"
	| "CONFIGURE_LAYOUT"
	| "MANAGE_POLICIES"
	| "CUSTOMER_SERVICE"
	| "VIEW_HISTORY"
	| "REPORTS_GENERATION";

export type CompanyAccessResponse = {
	companyId: string;
	companyName: string;
	userEmail: string;
	role: string;
	status: string;
	grantedPermissions: CompanyPermissionName[];
};

export type CompanyHierarchyResponse = {
	companyId: string;
	mermaidChart: string;
};

export type CompanyInvitationResponse = {
	invitationId: string;
	companyId: string;
	companyName: string;
	appointerUsername: string;
	appointeeUsername: string;
	invitationType: string;
	permissions: string[] | null;
};

export type InviteManagerRequest = {
	ownerUsername: string;
	usernameToInvite: string;
	permissions: CompanyPermissionName[];
};

export type InviteOwnerRequest = {
	ownerUsername: string;
	usernameToInvite: string;
};

export type DeleteEventRequest = {
	userEmail: string;
	eventManagerEmail: string;
};

type EventSummaryResponse = {
	eventId: string;
	companyId: string;
	companyName: string;
	companyRating: number;
	name: string;
	artist: string;
	eventType: string;
	date: string;
	location: string;
	rating: number;
	priceMin: number;
	priceMax: number;
	availableTickets: number;
	totalTickets: number;
};

function toEventSummary(response: EventSummaryResponse): EventSummary {
	return {
		id: response.eventId,
		companyId: response.companyId,
		companyName: response.companyName,
		companyRating: response.companyRating,
		name: response.name,
		artist: response.artist,
		type: response.eventType,
		date: response.date,
		location: response.location,
		rating: response.rating,
		priceMin: response.priceMin,
		priceMax: response.priceMax,
		availableTickets: response.availableTickets,
		totalTickets: response.totalTickets,
	};
}

export async function createCompany(
	request: CreateCompanyRequest,
): Promise<CompanyResponse> {
	const response = await api.post("/companies", request);

	return response.data.data as CompanyResponse;
}

export async function getCompanyHierarchy(
	companyId: string,
	requester: string,
): Promise<CompanyHierarchyResponse> {
	const response = await api.get(`/companies/${companyId}/hierarchy`, {
		params: { requester },
	});

	return response.data.data as CompanyHierarchyResponse;
}

export async function getCompanyPermissions(
	companyId: string,
	userEmail: string,
): Promise<CompanyAccessResponse> {
	const response = await api.get(`/companies/${companyId}/permissions`, {
		params: { userEmail },
	});

	return response.data.data as CompanyAccessResponse;
}

export async function getMyCompanies(userEmail: string): Promise<CompanyMembership[]> {
	const response = await api.get("/companies/me/companies", {
		params: { userEmail },
	});

	return response.data.data as CompanyMembership[];
}

export async function getMyInvitations(userEmail: string): Promise<CompanyInvitationResponse[]> {
	const response = await api.get("/companies/me/invitations", {
		params: { userEmail },
	});

	return response.data.data as CompanyInvitationResponse[];
}

export async function inviteCompanyManager(
	companyId: string,
	request: InviteManagerRequest,
): Promise<CompanyInvitationResponse> {
	const response = await api.post(`/companies/${companyId}/managers/invite`, request);

	return response.data.data as CompanyInvitationResponse;
}

export async function inviteCompanyOwner(
	companyId: string,
	request: InviteOwnerRequest,
): Promise<CompanyInvitationResponse> {
	const response = await api.post(`/companies/${companyId}/owners/invite`, request);

	return response.data.data as CompanyInvitationResponse;
}

export async function acceptInvitation(
	companyId: string,
	invitationId: string,
	username: string,
): Promise<void> {
	await api.post(`/companies/${companyId}/invitations/${invitationId}/accept`, null, {
		params: { username },
	});
}

export async function rejectInvitation(
	companyId: string,
	invitationId: string,
	username: string,
): Promise<void> {
	await api.post(`/companies/${companyId}/invitations/${invitationId}/reject`, null, {
		params: { username },
	});
}

export async function getEventsForUserInCompany(
	userEmail: string,
	companyId: string,
): Promise<EventSummary[]> {
	const response = await api.get(`/events/companies/${companyId}/users`, {
		params: { userEmail },
	});

	const rows = (response.data.data ?? []) as EventSummaryResponse[];
	return rows.map(toEventSummary);
}

export async function deleteEvent(
	eventId: string,
	request: DeleteEventRequest,
): Promise<void> {
	await api.delete(`/events/${eventId}`, {
		data: request,
	});
}
