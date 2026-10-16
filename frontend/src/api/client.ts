const API_BASE = '/api'

type ApiEnvelope<T> = {
  readonly success?: boolean
  readonly data?: T
  readonly error?: unknown
  readonly message?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/** 将后端或网络异常规整为可安全展示的中文消息。 */
export function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim()) return error.message
  if (typeof error === 'string' && error.trim()) return error
  if (isRecord(error)) {
    const message = error.message
    if (typeof message === 'string' && message.trim()) return message
    const detail = error.error
    if (typeof detail === 'string' && detail.trim()) return detail
  }
  return '请求未完成，请稍后重试。'
}

async function readEnvelope<T>(response: Response): Promise<ApiEnvelope<T>> {
  const body = await response.text()
  if (!body.trim()) {
    if (!response.ok) {
      throw new Error(`请求失败（${response.status}）：后端服务不可用或未返回响应。`)
    }
    throw new Error('后端未返回数据。')
  }
  try {
    return JSON.parse(body) as ApiEnvelope<T>
  } catch {
    if (!response.ok) {
      throw new Error(`请求失败（${response.status}）：后端返回了无法解析的响应。`)
    }
    throw new Error('后端返回了无法解析的数据。')
  }
}

export async function request<T>(url: string, options?: RequestInit): Promise<T> {
  let response: Response
  try {
    const headers = new Headers(options?.headers)
    if (!(options?.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    response = await fetch(`${API_BASE}${url}`, {
      ...options,
      headers,
    })
  } catch {
    throw new Error('无法连接后端服务，请确认后端已启动。')
  }
  const envelope = await readEnvelope<T>(response)
  if (!response.ok) {
    throw new Error(errorMessage(envelope.error ?? envelope.message ?? `请求失败（${response.status}）`))
  }
  if (!envelope.success) {
    throw new Error(errorMessage(envelope.error ?? envelope.message))
  }
  if (envelope.data === undefined) throw new Error('后端未返回数据。')
  return envelope.data
}
