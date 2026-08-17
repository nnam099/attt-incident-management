export type IncidentStatus = 'NEW' | 'TRIAGE' | 'INVESTIGATING' | 'CONTAINED' | 'RECOVERED' | 'RESOLVED' | 'CLOSED' | 'REOPENED';
export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ResolutionType = 'TRUE_POSITIVE' | 'FALSE_POSITIVE' | 'BENIGN' | 'NOT_APPLICABLE';
export type IoCType = 'IPV4' | 'DOMAIN' | 'URL' | 'MD5_HASH' | 'SHA256_HASH' | 'EMAIL_ADDRESS' | 'FILE_PATH';

export interface IoCResponse {
    id: number;
    type: IoCType;
    value: string;
    description?: string;
    createdAt: string;
}

export interface TaskResponse {
    id: number;
    taskName: string;
    isCompleted: boolean;
    completedAt?: string;
    completedByUsername?: string;
}

export interface IncidentResponse {
    id: number;
    incidentCode: string;
    title: string;
    description: string;
    affectedSystem: string;
    categoryName: string;
    severity: IncidentSeverity;
    status: IncidentStatus;
    reportedByUsername: string;
    assignedToUsername: string | null;
    detectedAt: string;
    ackDueAt: string;
    acknowledgedAt?: string;
    resolveDueAt: string;
    resolutionType?: ResolutionType;
    createdAt: string;
    updatedAt: string;
    iocs: IoCResponse[];
    tasks: TaskResponse[];
}

export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}
