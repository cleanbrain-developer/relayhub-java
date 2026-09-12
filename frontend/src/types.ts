export type Status = "ACTIVE" | "INACTIVE";
export type HttpVerb = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
export type Operation = "CREATED" | "REPLACED" | "PATCHED" | "DELETED";
export type DeliveryState = "PENDING" | "SUCCEEDED" | "DEAD";
export type DeliveryStatus = "SUCCESS" | "FAILED";

export interface Source {
  id: string;
  key: string;
  name: string;
  description: string;
  status: Status;
  createdAt: string;
}

export interface SourceEvent {
  id: string;
  sourceKey: string;
  key: string;
  resourceType: string;
  operation: Operation;
  ingressMethod: HttpVerb;
  ingressPath: string;
  status: Status;
}

export interface Target {
  id: string;
  key: string;
  name: string;
  description: string;
  baseUrl: string;
  status: Status;
}

export interface Subscription {
  id: string;
  sourceKey: string;
  sourceEventKey: string;
  targetKey: string;
  name: string;
  description: string;
  targetMethod: HttpVerb;
  targetPath: string;
  targetPayloadTemplate: string;
  retryPolicy: string | null;
  status: Status;
}

export interface Delivery {
  id: string;
  eventId: string;
  subscriptionId: string;
  targetId: string;
  state: DeliveryState;
  attemptCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface DeliveryAttempt {
  id: string;
  attemptNumber: number;
  status: DeliveryStatus;
  httpStatus: number | null;
  errorMessage: string | null;
  attemptedAt: string;
}

export interface DeliverySummary {
  pending: number;
  succeeded: number;
  dead: number;
}
