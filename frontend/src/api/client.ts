/**
 * Minimal same-origin API client. Dev traffic is proxied to Spring on 8080
 * by Vite; the packaged desktop app will serve the frontend from Spring
 * itself, so every path stays relative and CORS never enters the picture.
 * Failures surface as Error carries ProblemDetail details.
 */
export class ApiError extends Error {
  readonly status: number
  readonly problemTitle: string | undefined

  constructor(status: number, problemTitle: string | undefined, detail: string) {
    super(detail)
    this.status = status
    this.problemTitle = problemTitle
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) {
    throw await toApiError(response)
  }
  return response.json() as Promise<T>
}

/** 204-aware GET for endpoints that return "no current value". */
export async function apiGetOptional<T>(path: string): Promise<T | null> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
  })

  if (response.status === 204) {
    return null
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  return response.json() as Promise<T>
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers:
      body === undefined ? { Accept: 'application/json' } : {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (!response.ok) {
    throw await toApiError(response)
  }
  return response.json() as Promise<T>
}

async function toApiError(response: Response): Promise<ApiError> {
  let title: string | undefined
  let detail = `Request failed (${response.status})`

  try {
    const problem = (await response.json()) as { title?: string; detail?: string }
    title = problem.title
    if (problem.detail) {
      detail = problem.detail
    }
  } catch {
    // Non-JSON error body: keep the generic message.
  }
  return new ApiError(response.status, title, detail)
}
