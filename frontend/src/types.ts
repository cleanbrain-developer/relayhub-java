export type Status = "ACTIVE" | "INACTIVE";
export type HttpVerb = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
export type Operation = "CREATED" | "REPLACED" | "PATCHED" | "DELETED";
export type DeliveryState = "PENDING" | "PROCESSING" | "SUCCEEDED" | "RETRYING" | "DEAD" | "REPLAYING";
export type DeliveryStatus = "SUCCESS" | "FAILED";
export type FieldDataType = "STRING" | "NUMBER" | "BOOLEAN" | "OBJECT" | "ARRAY" | "DATE";
/** Only NONE/API_KEY are functionally wired today — the rest are declared server-side for a later
 *  stage. See AuthenticationType.java. */
export type AuthenticationType = "NONE" | "API_KEY" | "HMAC" | "OAUTH2" | "BEARER_TOKEN" | "BASIC";

export interface Source {
  id: string;
  key: string;
  name: string;
  description: string;
  authenticationType: AuthenticationType;
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
  authenticationType: AuthenticationType;
  status: Status;
}

export interface TargetEndpoint {
  id: string;
  targetKey: string;
  key: string;
  name: string;
  description: string;
  httpMethod: HttpVerb;
  path: string;
  timeoutOverrideMs: number | null;
  headers: string | null;
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
  targetEndpointKey: string;
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
  targetEndpointKey: string;
  name: string;
  description: string;
  targetMethod: HttpVerb;
  targetPath: string;
  targetPayloadTemplate: string;
  filterExpression: string | null;
  maxAttempts: number | null;
  effectiveMaxAttempts: number;
  initialBackoffMs: number | null;
  maxBackoffMs: number | null;
  backoffMultiplier: number | null;
  jitter: boolean | null;
  timeoutMs: number | null;
  status: Status;
  /** Field-registry validation warnings (informational only, never blocks save) — see
   *  MappingValidationService.java. Empty when there's nothing to warn about. */
  mappingWarnings: string[];
}

export interface Delivery {
  id: string;
  eventId: string;
  subscriptionId: string;
  targetId: string;
  state: DeliveryState;
  attemptCount: number;
  nextAttemptAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CanonicalEvent {
  id: string;
  sourceId: string;
  sourceEventId: string;
  resourceType: string;
  resourceId: string;
  operation: Operation;
  occurredAt: string | null;
  receivedAt: string;
  idempotencyKey: string | null;
  payload: unknown;
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

export interface DeliverySettings {
  maxAttempts: number;
  autoReplayIntervalMs: number;
}
