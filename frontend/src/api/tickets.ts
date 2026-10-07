import { http } from './http'

import type {
  CreateTicketInput,
  Ticket,
  TicketPage,
  TicketPriority,
  TicketStatus,
  UpdateTicketInput,
} from '@/types/ticket'

/**
 * F1/F2 surface: read endpoints + create + basic-info patch. Assign /
 * lifecycle actions belong to F3 and are deliberately absent here.
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
