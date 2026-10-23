export type SseHandlers = {
  onMessage?: (data: string) => void
  onFollowups?: (data: string) => void
  onDone?: () => void
}

function dispatchEvent(block: string, handlers: SseHandlers): boolean {
  let eventName = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).trimStart())
    }
  }
  const payload = data.join('\n')
  if (eventName === 'done' || payload === '[DONE]') {
    handlers.onDone?.()
    return true
  } else if (eventName === 'followups') {
    handlers.onFollowups?.(payload)
  } else if (eventName === 'message') {
    handlers.onMessage?.(payload)
  }
  return false
}

export async function consumeSse(
  url: string,
  body: unknown,
  handlers: SseHandlers,
  signal: AbortSignal
): Promise<void> {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok) {
    throw new Error(`SSE request failed: ${response.status}`)
  }
  if (!response.body) {
    throw new Error('SSE response body is empty')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let doneEventReceived = false

  const consumeBuffer = () => {
    buffer = buffer.replace(/\r\n/g, '\n')
    let separator = buffer.indexOf('\n\n')
    while (separator >= 0) {
      const block = buffer.slice(0, separator)
      buffer = buffer.slice(separator + 2)
      if (block.trim()) {
        doneEventReceived = dispatchEvent(block, handlers) || doneEventReceived
      }
      separator = buffer.indexOf('\n\n')
    }
  }

  let completedNormally = false
  try {
    while (true) {
      const result = await reader.read()
      buffer += decoder.decode(result.value || new Uint8Array(), { stream: !result.done })
      consumeBuffer()
      if (result.done) break
    }
    if (buffer.trim()) {
      doneEventReceived = dispatchEvent(buffer, handlers) || doneEventReceived
    }
    completedNormally = true
  } finally {
    reader.releaseLock()
    if (!completedNormally) {
      void reader.cancel()
    } else if (!doneEventReceived) {
      handlers.onDone?.()
    }
  }
}
