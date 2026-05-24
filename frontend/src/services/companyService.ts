import api from "./api";

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
