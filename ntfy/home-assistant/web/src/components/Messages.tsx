import { useEffect, useRef, useState } from 'preact/hooks'
import { api, createEventSource } from '../api.ts'

interface Topic {
  id: number
  name: string
  latest_body: string | null
  latest_sent_at: string | null
  status: string
}

interface Message {
  id?: number
  sender: string | null
  body: string
  sent_at: string
  is_mine?: boolean
}

export function Messages() {
  const [topics, setTopics] = useState<Topic[]>([])
  const [adding, setAdding] = useState(false)
  const [newTopic, setNewTopic] = useState('')
  const [activeTopic, setActiveTopic] = useState<Topic | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const chatEndRef = useRef<HTMLDivElement | null>(null)
  const esRef = useRef<EventSource | null>(null)

  const loadTopics = async () => {
    const res = await api('/api/topics')
    setTopics(await res.json())
  }

  const addTopic = async (e: Event) => {
    e.preventDefault()
    const name = newTopic.trim()
    if (!name) return
    const res = await api('/api/topics', {
      method: 'POST',
      body: new URLSearchParams({ name }),
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    })
    if (res.ok) {
      setNewTopic('')
      setAdding(false)
      loadTopics()
    } else {
      const data = await res.json()
      alert(data.detail || 'Failed to add topic')
    }
  }

  const deleteTopic = async (id: number) => {
    if (!confirm('Delete this topic?')) return
    const res = await api(`/api/topics/${id}`, { method: 'DELETE' })
    if (res.ok) {
      if (activeTopic?.id === id) closeChat()
      loadTopics()
    }
  }

  const openChat = async (topic: Topic) => {
    setActiveTopic(topic)
    const res = await api(`/api/topics/${topic.id}/messages`)
    const msgs: Message[] = await res.json()
    setMessages(msgs)
    connectSSE(topic.name)
  }

  const closeChat = () => {
    setActiveTopic(null)
    setMessages([])
    if (esRef.current) {
      esRef.current.close()
      esRef.current = null
    }
  }

  const sendMessage = async (e: Event) => {
    e.preventDefault()
    if (!activeTopic || !input.trim()) return
    setSending(true)
    const res = await api(`/api/topics/${activeTopic.id}/messages`, {
      method: 'POST',
      body: new URLSearchParams({ body: input.trim() }),
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    })
    setSending(false)
    if (res.ok) {
      setInput('')
    } else {
      const data = await res.json()
      alert(data.detail || 'Failed to send message')
    }
  }

  const connectSSE = (topicName: string) => {
    if (esRef.current) esRef.current.close()
    const es = createEventSource('/api/messages/stream')
    es.onmessage = (e) => {
      const data = JSON.parse(e.data)
      if (data.type === 'message' && data.topic === topicName) {
        setMessages((prev) => [
          ...prev,
          {
            sender: data.sender,
            body: data.body,
            sent_at: data.sent_at || new Date().toISOString(),
          },
        ])
      }
    }
    es.onerror = () => {
      es.close()
      setTimeout(() => connectSSE(topicName), 3000)
    }
    esRef.current = es
  }

  useEffect(() => {
    loadTopics()
    return () => {
      if (esRef.current) esRef.current.close()
    }
  }, [])

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const formatTime = (iso: string) => {
    if (!iso) return ''
    const d = new Date(iso)
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  if (activeTopic) {
    return (
      <section>
        <div class="chat-header">
          <button class="btn btn-small" onClick={closeChat}>← Back</button>
          <span class="chat-title">{activeTopic.name}</span>
        </div>
        <div class="chat-messages">
          {messages.map((msg, idx) => {
            const isMine = msg.is_mine ?? false
            return (
              <div key={idx} class={`message-row ${isMine ? 'mine' : 'theirs'}`}>
                <div class="message-bubble">
                  <div class="message-meta">
                    {isMine ? 'You' : msg.sender || 'Unknown'} · {formatTime(msg.sent_at)}
                  </div>
                  <div>{msg.body}</div>
                </div>
              </div>
            )
          })}
          <div ref={chatEndRef} />
        </div>
        <form class="chat-input-bar" onSubmit={sendMessage}>
          <input
            type="text"
            value={input}
            onInput={(e) => setInput((e.target as HTMLInputElement).value)}
            placeholder="Message"
            autoComplete="off"
            disabled={sending}
          />
          <button type="submit" class="btn-primary" disabled={sending}>
            {sending ? 'Sending...' : 'Send'}
          </button>
        </form>
      </section>
    )
  }

  return (
    <section>
      <div class="panel">
        {!adding ? (
          <button class="btn" onClick={() => setAdding(true)}>+ Add topic</button>
        ) : (
          <form class="inline-form" onSubmit={addTopic}>
            <input
              type="text"
              value={newTopic}
              onInput={(e) => setNewTopic((e.target as HTMLInputElement).value)}
              placeholder="topic-name"
              required
            />
            <button type="submit" class="btn-primary">Save</button>
            <button type="button" class="btn" onClick={() => setAdding(false)}>Cancel</button>
          </form>
        )}
      </div>

      <div class="card-list">
        {topics.length === 0 && <p class="muted">No topics yet. Add one to start chatting.</p>}
        {topics.map((topic) => (
          <div
            key={topic.id}
            class="card topic-item"
            onClick={() => openChat(topic)}
          >
            <div class="topic-main">
              <span class="topic-status" title={topic.status || 'unknown'} />
              <div class="topic-info">
                <div class="topic-name">
                  {topic.name}
                  {topic.status === 'write_only' && <span class="tag">send-only</span>}
                </div>
                <div class="topic-preview">{topic.latest_body || 'No messages yet'}</div>
              </div>
            </div>
            <button
              class="btn btn-small topic-delete"
              onClick={(e: Event) => {
                e.stopPropagation()
                deleteTopic(topic.id)
              }}
            >
              Delete
            </button>
          </div>
        ))}
      </div>
    </section>
  )
}
