import { http } from './http'

import type {
  AssignTicketInput,
  CreateTicketInput,
  Ticket,
  TicketActionInput,
  TicketPage,
  TicketPriority,
  TicketStatus,
  UpdateTicketInput,
} from '@/types/ticket'

/**
 * Ticket API surface: read endpoints + create + basic-info patch +
 * assignment + lifecycle actions. Each call returns the full ticket with
 * the freshly incremented version. CLAIM has no backend endpoint in V1 and
 * is deliberately absent here.
 */
export interface TicketListParams {
  status?: TicketStatus
  priority?: TicketPriority
  /** 0-based, exactly as the backend expects. */
  page: number
  size: number
}

export async function listTickets(params: TicketListParams): Promise<TicketPage> {
  const { data } = await http.get<TicketPage>('/v1/tickets', { params })
  return data
}

export async function getTicket(id: number): Promise<Ticket> {
  const { data } = await http.get<Ticket>(`/v1/tickets/${id}`)
  return data
}

/** POST /api/v1/tickets -> 201 with the full saved ticket (version 0). */
export async function createTicket(input: CreateTicketInput): Promise<Ticket> {
  const { data } = await http.post<Ticket>('/v1/tickets', input)
  return data
}

/**
 * PATCH /api/v1/tickets/{id} -> 200 with the updated ticket (version +1).
 * `input` must follow diff-only semantics: changed business fields + version.
 */
export async function updateTicket(id: number, input: UpdateTicketInput): Promise<Ticket> {
  const { data } = await http.patch<Ticket>(`/v1/tickets/${id}`, input)
  return data
}

/**
 * POST /v1/tickets/{id}/assign -> 200. Shared by assign and reassign: the
 * backend domain decides ASSIGN vs REASSIGN, the client never sends a type.
 */
export async function assignTicket(id: number, input: AssignTicketInput): Promise<Ticket> {
  const { data } = await http.post<Ticket>(`/v1/tickets/${id}/assign`, input)
  return data
}

export async function startTicket(id: number, input: TicketActionInput): Promise<Ticket> {
  const { data } = await http.post<Ticket>(`/v1/tickets/${id}/start`, input)
  return data
}

export async function resolveTicket(id: number, input: TicketActionInput): Promise<Ticket> {
  const { data } = await http.post<Ticket>(`/v1/tickets/${id}/resolve`, input)
  return data
}

export async function closeTicket(id: number, input: TicketActionInput): Promise<Ticket> {
  const { data } = await http.post<Ticket>(`/v1/tickets/${id}/close`, input)
  return data
}

export async function reopenTicket(id: number, input: TicketActionInput): Promise<Ticket> {
  const { data } = await http.post<Ticket>(`/v1/tickets/${id}/reopen`, input)
  return data
}
