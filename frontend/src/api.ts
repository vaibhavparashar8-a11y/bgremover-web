// The single typed API client: every backend call in the app goes through here.

export interface ModelInfo {
  name: string
  label: string
  quality: string
  speed: string
  size_mb: number
  interactive?: boolean
}

export interface ModelsResponse {
  active: string
  models: ModelInfo[]
}

export interface PromptPoint {
  x: number
  y: number
  label: 0 | 1
}

export interface PromptBox {
  x1: number
  y1: number
  x2: number
  y2: number
}

export type PromptEntry = PromptPoint | PromptBox

export interface RemoveOptions {
  model?: string
  alphaMatting?: boolean
  points?: PromptEntry[]
  invert?: boolean
}

async function errorDetail(res: Response): Promise<string> {
  try {
    const body: unknown = await res.json()
    if (body && typeof body === 'object' && 'detail' in body) {
      const detail = (body as { detail: unknown }).detail
      return typeof detail === 'string' ? detail : JSON.stringify(detail)
    }
  } catch {
    /* non-JSON error body */
  }
  return `Request failed (${res.status})`
}

export async function fetchModels(): Promise<ModelsResponse> {
  const res = await fetch('/api/models')
  if (!res.ok) throw new Error(await errorDetail(res))
  return (await res.json()) as ModelsResponse
}

export async function setActiveModel(name: string): Promise<ModelsResponse> {
  const res = await fetch('/api/models/active', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!res.ok) throw new Error(await errorDetail(res))
  return (await res.json()) as ModelsResponse
}

export async function removeBackground(file: File, options: RemoveOptions): Promise<Blob> {
  const form = new FormData()
  form.append('file', file)
  if (options.model) form.append('model', options.model)
  form.append('alphaMatting', String(options.alphaMatting ?? false))
  form.append('invert', String(options.invert ?? false))
  if (options.points && options.points.length > 0) {
    form.append('points', JSON.stringify(options.points))
  }
  const res = await fetch('/api/remove', { method: 'POST', body: form })
  if (!res.ok) throw new Error(await errorDetail(res))
  return res.blob()
}
