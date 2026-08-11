export interface IncidentResponse {
    id: number;
    incidentCode: string;
    title: string;
    description: string;
    affectedSystem: string;
    categoryName: string;
    severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
    status: 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
    reportedByUsername: string;
    assignedToUsername: string | null;
    detectedAt: string;
    slaDueAt: string;
    createdAt: string;
    updatedAt: string;
}

export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}
