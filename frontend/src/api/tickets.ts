import { http } from './http'

import type { Ticket, TicketPage, TicketPriority, TicketStatus } from '@/types/ticket'

/**
 * F1 surface: read endpoints only. Create / patch / assign / lifecycle
 * actions belong to F2 / F3 and are deliberately absent here.
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
