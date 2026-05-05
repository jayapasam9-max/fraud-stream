import React, { useState, useEffect, useRef, useCallback } from 'react'
import {
  BarChart, Bar, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts'

const RULE_COLORS = {
  VELOCITY: '#ef4444',
  AMOUNT_DEVIATION: '#f97316',
  GEO_IMPOSSIBLE: '#8b5cf6',
  HIGH_RISK_MERCHANT: '#ec4899',
  ROUND_AMOUNT_BURST: '#06b6d4',
}

const RULE_LABELS = {
  VELOCITY: 'Velocity',
  AMOUNT_DEVIATION: 'Amt Dev',
  GEO_IMPOSSIBLE: 'Geo Imp',
  HIGH_RISK_MERCHANT: 'Hi Risk MCC',
  ROUND_AMOUNT_BURST: 'Round Burst',
}

function timeAgo(isoStr) {
  if (!isoStr) return '—'
  const diff = Math.floor((Date.now() - new Date(isoStr)) / 1000)
  if (diff < 60) return `${diff}s ago`
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`
  return `${Math.floor(diff / 3600)}h ago`
}

function StatCard({ label, value, color }) {
  return (
    <div className="bg-gray-900 rounded-xl border border-gray-800 p-4">
      <p className="text-xs text-gray-400 mb-1 uppercase tracking-wide">{label}</p>
      <p className={`text-2xl font-bold ${color}`}>{value}</p>
    </div>
  )
}

function ChartCard({ title, children }) {
  return (
    <div className="bg-gray-900 rounded-xl border border-gray-800 p-4">
      <p className="text-sm font-semibold text-gray-300 mb-3">{title}</p>
      {children}
    </div>
  )
}

function RulePill({ rule }) {
  const color = RULE_COLORS[rule] || '#6b7280'
  const label = RULE_LABELS[rule] || rule
  return (
    <span
      className="shrink-0 text-xs font-semibold px-2 py-0.5 rounded-full whitespace-nowrap"
      style={{ background: color + '22', color }}
    >
      {label}
    </span>
  )
}

function ScoreBadge({ score }) {
  const s = typeof score === 'number' ? score : parseFloat(score) || 0
  const color = s >= 0.8 ? 'text-red-400' : s >= 0.5 ? 'text-yellow-400' : 'text-gray-400'
  return (
    <span className={`text-xs font-mono font-bold ${color}`}>
      {(s * 100).toFixed(0)}%
    </span>
  )
}

const CustomTooltip = ({ active, payload, label }) => {
  if (active && payload && payload.length) {
    return (
      <div className="bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-xs">
        <p className="text-gray-300">{label}</p>
        <p className="text-red-400 font-bold">{payload[0].value} flags</p>
      </div>
    )
  }
  return null
}

export default function App() {
  const [flags, setFlags] = useState([])
  const [stats, setStats] = useState(null)
  const [connected, setConnected] = useState(false)
  const [timeline, setTimeline] = useState([])
  const wsRef = useRef(null)
  const reconnectTimer = useRef(null)

  // Fetch initial data
  useEffect(() => {
    fetch('/api/flags?limit=50')
      .then(r => r.ok ? r.json() : [])
      .then(data => setFlags(Array.isArray(data) ? data.slice(0, 50) : []))
      .catch(() => {})

    fetch('/api/stats')
      .then(r => r.ok ? r.json() : null)
      .then(data => { if (data) setStats(data) })
      .catch(() => {})
  }, [])

  const connect = useCallback(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/flags`)
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
      clearTimeout(reconnectTimer.current)
    }

    ws.onclose = () => {
      setConnected(false)
      reconnectTimer.current = setTimeout(connect, 3000)
    }

    ws.onerror = () => ws.close()

    ws.onmessage = (e) => {
      try {
        const flag = JSON.parse(e.data)
        setFlags(prev => [flag, ...prev].slice(0, 50))
        setStats(prev => {
          if (!prev) return { totalFlags: 1, flagsByRule: { [flag.ruleId]: 1 } }
          const flagsByRule = { ...prev.flagsByRule }
          flagsByRule[flag.ruleId] = (flagsByRule[flag.ruleId] || 0) + 1
          return { totalFlags: (prev.totalFlags || 0) + 1, flagsByRule }
        })
        const label = new Date(flag.flaggedAt).toLocaleTimeString([], {
          hour: '2-digit', minute: '2-digit'
        })
        setTimeline(prev => {
          const last = prev[prev.length - 1]
          if (last && last.label === label) {
            return [...prev.slice(0, -1), { label, count: last.count + 1 }]
          }
          return [...prev, { label, count: 1 }].slice(-30)
        })
      } catch {/* ignore parse errors */}
    }
  }, [])

  useEffect(() => {
    connect()
    return () => {
      wsRef.current?.close()
      clearTimeout(reconnectTimer.current)
    }
  }, [connect])

  const ruleChartData = stats?.flagsByRule
    ? Object.entries(stats.flagsByRule).map(([rule, count]) => ({
        rule: RULE_LABELS[rule] || rule,
        count,
        fill: RULE_COLORS[rule] || '#6b7280'
      }))
    : []

  const topRule = stats?.flagsByRule
    ? Object.entries(stats.flagsByRule).sort((a, b) => b[1] - a[1])[0]?.[0]
    : null

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 p-4 md:p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <span className="text-2xl font-bold tracking-tight">🛡️ FraudStream</span>
          <span className="hidden md:block text-gray-500 text-sm">Real-time fraud detection</span>
        </div>
        <div className="flex items-center gap-2 bg-gray-900 rounded-full px-3 py-1.5 border border-gray-800">
          <div className={`w-2 h-2 rounded-full ${connected ? 'bg-green-400 animate-pulse' : 'bg-red-500'}`} />
          <span className="text-xs text-gray-400">{connected ? 'Live' : 'Reconnecting…'}</span>
        </div>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
        <StatCard label="Flags (1hr)" value={stats?.totalFlags ?? '—'} color="text-red-400" />
        <StatCard label="Rules Active" value={stats?.flagsByRule ? Object.keys(stats.flagsByRule).length : '—'} color="text-purple-400" />
        <StatCard label="Top Rule" value={topRule ? (RULE_LABELS[topRule] || topRule) : '—'} color="text-orange-400" />
        <StatCard label="Feed Items" value={flags.length} color="text-cyan-400" />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-5">
        <ChartCard title="Flags by Rule">
          {ruleChartData.length === 0 ? (
            <div className="h-44 flex items-center justify-center text-gray-600 text-sm">
              Waiting for data…
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={ruleChartData} margin={{ top: 0, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
                <XAxis dataKey="rule" tick={{ fill: '#6b7280', fontSize: 10 }} />
                <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} />
                <Tooltip content={<CustomTooltip />} />
                <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                  {ruleChartData.map((entry, i) => (
                    <rect key={i} fill={entry.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        <ChartCard title="Flag Rate (live)">
          {timeline.length === 0 ? (
            <div className="h-44 flex items-center justify-center text-gray-600 text-sm">
              Waiting for WebSocket events…
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={180}>
              <LineChart data={timeline} margin={{ top: 0, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
                <XAxis dataKey="label" tick={{ fill: '#6b7280', fontSize: 10 }} />
                <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} />
                <Tooltip content={<CustomTooltip />} />
                <Line
                  type="monotone"
                  dataKey="count"
                  stroke="#ef4444"
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4 }}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </ChartCard>
      </div>

      {/* Live feed table */}
      <div className="bg-gray-900 rounded-xl border border-gray-800">
        <div className="px-4 py-3 border-b border-gray-800 flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-red-400 animate-pulse" />
          <span className="font-semibold text-sm">Live Flag Feed</span>
          <span className="ml-auto text-xs text-gray-600">{flags.length} events</span>
        </div>

        <div className="overflow-auto max-h-96 divide-y divide-gray-800">
          {flags.length === 0 ? (
            <div className="p-10 text-center">
              <p className="text-gray-500 text-sm">No flags yet.</p>
              <p className="text-gray-600 text-xs mt-1">
                Start the producer: <code className="bg-gray-800 px-1 rounded">cd producer && ./mvnw spring-boot:run</code>
              </p>
            </div>
          ) : (
            flags.map((flag, i) => (
              <div
                key={`${flag.txId}-${i}`}
                className="flex items-start gap-3 px-4 py-3 hover:bg-gray-800/50 transition-colors"
              >
                <RulePill rule={flag.ruleId} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <span className="text-xs font-mono text-gray-500">
                      {flag.txId?.slice(0, 8)}…
                    </span>
                    <span className="text-xs text-gray-700">•</span>
                    <span className="text-xs text-gray-400">{flag.accountId}</span>
                  </div>
                  <p className="text-sm text-gray-300 truncate">{flag.reason}</p>
                </div>
                <div className="flex flex-col items-end gap-1 shrink-0">
                  <ScoreBadge score={flag.score} />
                  <span className="text-xs text-gray-600">{timeAgo(flag.flaggedAt)}</span>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
