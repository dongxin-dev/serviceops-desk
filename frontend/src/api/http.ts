import axios from 'axios'
import type { AxiosError } from 'axios'

import type { ApiErrorResponse } from '@/types/ticket'

/**
 * Normalized frontend error. `backendMessage` is kept for debugging only -
 * UI copy must be produced from `code` via i18n, never from the raw
 * backend message alone.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly backendMessage: string,
  ) {
    super(`[${status}] ${code}: ${backendMessage}`)
    this.name = 'ApiError'
  }
}

/** Single shared instance; business code always uses relative /api paths. */
export const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: { Accept: 'application/json' },
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorResponse>) => Promise.reject(toApiError(error)),
)

function toApiError(error: AxiosError<ApiErrorResponse>): ApiError {
  const response = error.response
  if (!response) {
    // Network failure / timeout: no HTTP answer at all.
    return new ApiError(0, 'NetworkError', error.message ?? 'network error')
  }
  const body = response.data
  const code = body && typeof body.error === 'string' ? body.error : 'UnknownError'
  const message = body && typeof body.message === 'string' ? body.message : 'no details'
  return new ApiError(response.status, code, message)
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
