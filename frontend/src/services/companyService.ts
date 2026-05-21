import api from "./api";

export type CreateCompanyRequest = {
	founderUsername: string;
	companyName: string;
};

export type CompanyResponse = {
	id: string;
	name: string;
	founderUsername: string;
	rating: number;
	isActive: boolean;
	eventIds: string[];
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
