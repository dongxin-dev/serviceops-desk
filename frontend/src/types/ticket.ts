/**
 * TypeScript contract mirroring the backend Ticket module DTOs exactly.
 * Enum wire values MUST stay identical to the backend (TicketStatus.java /
 * TicketPriority.java); localization happens only at the display layer.
 */

export type TicketStatus =
  | 'OPEN'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'CLOSED'
  | 'REOPENED'

export type TicketPriority = 'P1_CRITICAL' | 'P2_HIGH' | 'P3_MEDIUM' | 'P4_LOW'

/** Element of GET /api/v1/tickets -> TicketSummaryResponse. */
export interface TicketSummary {
  id: number
  ticketNo: string
  title: string
  status: TicketStatus
  priority: TicketPriority
  currentAssigneeId: number | null
  createdAt: string
}

/** Stable pagination envelope -> TicketListResponse (page is 0-based). */
export interface TicketPage {
  content: TicketSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Full read model -> TicketResponse. Dates are ISO-8601 offset strings. */
export interface Ticket {
  id: number
  ticketNo: string
  title: string
  description: string
  category: string
  priority: TicketPriority
  status: TicketStatus
  requesterId: number
  currentAssigneeId: number | null
  slaPolicyId: number
  responseDueAt: string
  resolutionDueAt: string
  firstRespondedAt: string | null
  resolvedAt: string | null
  closedAt: string | null
  escalationLevel: number
  version: number
  createdAt: string
  updatedAt: string
}

/** Unified error envelope -> ApiErrorResponse. */
export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}

/**
 * Body of POST /api/v1/tickets -> CreateTicketRequest. Everything else
 * (ticketNo / status / SLA / version / timestamps) is derived server-side
 * and MUST NOT be sent from the client.
 */
export interface CreateTicketInput {
  title: string
  description: string
  category: string
  priority: TicketPriority
  requesterId: number
}

/**
 * Body of PATCH /api/v1/tickets/{id} -> UpdateTicketRequest.
 * version is the optimistic-lock token and is always carried; a business
 * field is included ONLY when it actually changed - absent means "keep",
 * and an explicit null is rejected by the backend with 400.
 */
export interface UpdateTicketInput {
  version: number
  title?: string
  description?: string
  category?: string
}
