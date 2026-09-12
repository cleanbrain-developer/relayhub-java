export type Status = "ACTIVE" | "INACTIVE";
export type HttpVerb = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
export type Operation = "CREATED" | "REPLACED" | "PATCHED" | "DELETED";
export type DeliveryState = "PENDING" | "SUCCEEDED" | "DEAD";
export type DeliveryStatus = "SUCCESS" | "FAILED";
export type FieldDataType = "STRING" | "NUMBER" | "BOOLEAN" | "OBJECT" | "ARRAY" | "DATE";

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
  name: string;
  description: string;
  resourceType: string;
  operation: Operation;
  ingressMethod: HttpVerb;
  ingressPath: string;
  resourceIdPath: string;
  occurredAtPath: string | null;
  idempotencyKeyPath: string | null;
  idempotencyHeader: string | null;
  payloadSchema: string | null;
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

export interface SourceField {
  id: string;
  sourceKey: string;
  sourceEventKey: string;
  key: string;
  jsonPath: string;
  dataType: FieldDataType;
  description: string | null;
  exampleValue: string | null;
  required: boolean;
  sensitive: boolean;
  status: Status;
}

export interface TargetField {
  id: string;
  targetKey: string;
  key: string;
  dataType: FieldDataType;
  description: string | null;
  exampleValue: string | null;
  required: boolean;
  sensitive: boolean;
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
  deliveryId: string;
  attemptNumber: number;
  status: DeliveryStatus;
  requestMethod: string | null;
  requestUrl: string | null;
  requestBody: string | null;
  httpStatus: number | null;
  responseBody: string | null;
  errorMessage: string | null;
  attemptedAt: string;
}

export interface DeliverySummary {
  pending: number;
  succeeded: number;
  dead: number;
}
