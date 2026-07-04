import { useCallback, useEffect, useRef, useState } from 'react'
import type { ModelInfo, PromptBox, PromptEntry, PromptPoint } from './api'
import { fetchModels, removeBackground, setActiveModel } from './api'

type Mode = 'auto' | 'select'
type SelectTool = 'point' | 'brush'
type SelectionAction = 'keep' | 'remove'
type EditTool = 'restore' | 'erase'

interface XY {
  x: number
  y: number
}

const BRUSH_SAMPLE_LIMIT = 12

export default function App() {
  const [models, setModels] = useState<ModelInfo[]>([])
  const [model, setModel] = useState('')
  const [mode, setMode] = useState<Mode>('auto')
  const [selectTool, setSelectTool] = useState<SelectTool>('point')
  const [selectionAction, setSelectionAction] = useState<SelectionAction>('keep')
  const [points, setPoints] = useState<PromptPoint[]>([])
  const [box, setBox] = useState<PromptBox | null>(null)
  const [dragBox, setDragBox] = useState<PromptBox | null>(null)
  const [stroke, setStroke] = useState<XY[]>([])
  const [alphaMatting, setAlphaMatting] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [originalUrl, setOriginalUrl] = useState<string | null>(null)
  const [resultUrl, setResultUrl] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const [slider, setSlider] = useState(50)
  const [elapsed, setElapsed] = useState(0)
  // post-result brush editor
  const [editing, setEditing] = useState(false)
  const [editTool, setEditTool] = useState<EditTool>('restore')
  const [brush, setBrush] = useState(40)
  const [undoCount, setUndoCount] = useState(0)

  const inputRef = useRef<HTMLInputElement>(null)
  const imgRef = useRef<HTMLImageElement>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const pressRef = useRef<{ start: XY; moved: boolean } | null>(null)
  const strokeRef = useRef<XY[] | null>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const origImgRef = useRef<HTMLImageElement | null>(null)
  const undoRef = useRef<ImageData[]>([])
  const editStrokeRef = useRef<XY | null>(null)

  useEffect(() => {
    fetchModels()
      .then((data) => {
        setModels(data.models)
        setModel(data.active)
      })
      .catch(() => setError('Could not load model list — is the inference service running?'))
  }, [])

  const autoModels = models.filter((m) => !m.interactive)
  const samMeta = models.find((m) => m.name === 'sam')

  const dropResult = useCallback(() => {
    setResultUrl((old) => {
      if (old) URL.revokeObjectURL(old)
      return null
    })
  }, [])

  const acceptFile = useCallback(
    (f: File | undefined | null) => {
      if (!f) return
      if (!f.type.startsWith('image/')) {
        setError('Please choose an image file.')
        return
      }
      setError(null)
      setFile(f)
      setPoints([])
      setBox(null)
      setStroke([])
      setEditing(false)
      dropResult()
      setOriginalUrl((old) => {
        if (old) URL.revokeObjectURL(old)
        return URL.createObjectURL(f)
      })
    },
    [dropResult],
  )

  const chooseModel = (name: string) => {
    setModel(name)
    // keep the server-side default in sync so other clients/tools agree
    setActiveModel(name).catch(() => setError('Could not set the active model.'))
  }

  // ---- coordinate mapping ----

  const toNatural = (e: { clientX: number; clientY: number }): XY => {
    const img = imgRef.current
    if (!img) return { x: 0, y: 0 }
    const rect = img.getBoundingClientRect()
    const x = Math.round(((e.clientX - rect.left) / rect.width) * img.naturalWidth)
    const y = Math.round(((e.clientY - rect.top) / rect.height) * img.naturalHeight)
    return {
      x: Math.min(Math.max(x, 0), img.naturalWidth - 1),
      y: Math.min(Math.max(y, 0), img.naturalHeight - 1),
    }
  }

  // ---- select mode: point/box tool ----

  const selDown = (e: React.PointerEvent<HTMLImageElement>) => {
    if (mode !== 'select' || busy || e.button !== 0) return
    e.currentTarget.setPointerCapture(e.pointerId)
    if (selectTool === 'brush') {
      strokeRef.current = [toNatural(e)]
      setStroke(strokeRef.current)
      return
    }
    pressRef.current = { start: toNatural(e), moved: false }
  }

  const selMove = (e: React.PointerEvent<HTMLImageElement>) => {
    if (selectTool === 'brush') {
      if (!strokeRef.current) return
      strokeRef.current = [...strokeRef.current, toNatural(e)]
      setStroke(strokeRef.current)
      return
    }
    const press = pressRef.current
    if (!press) return
    const p = toNatural(e)
    if (!press.moved && Math.hypot(p.x - press.start.x, p.y - press.start.y) < 8) return
    press.moved = true
    setDragBox({ x1: press.start.x, y1: press.start.y, x2: p.x, y2: p.y })
  }

  const selUp = (e: React.PointerEvent<HTMLImageElement>) => {
    if (selectTool === 'brush') {
      const s = strokeRef.current
      strokeRef.current = null
      setStroke([])
      if (s && s.length > 1) {
        // a brush stroke is a fast way to place many include-points for SAM
        const step = Math.max(1, Math.floor(s.length / BRUSH_SAMPLE_LIMIT))
        const sampled = s
          .filter((_, i) => i % step === 0)
          .slice(0, BRUSH_SAMPLE_LIMIT)
          .map((p): PromptPoint => ({ ...p, label: 1 }))
        setPoints((pts) => [...pts, ...sampled])
      }
      return
    }
    const press = pressRef.current
    pressRef.current = null
    if (!press) return
    const p = toNatural(e)
    if (press.moved) {
      setBox({
        x1: Math.min(press.start.x, p.x),
        y1: Math.min(press.start.y, p.y),
        x2: Math.max(press.start.x, p.x),
        y2: Math.max(press.start.y, p.y),
      })
      setDragBox(null)
    } else {
      setPoints((pts) => [...pts, { ...p, label: 1 }])
    }
  }

  // ---- processing ----

  const process = async () => {
    if (!file || busy) return
    setBusy(true)
    setError(null)
    setElapsed(0)
    timerRef.current = setInterval(() => setElapsed((s) => s + 1), 1000)
    try {
      const prompt: PromptEntry[] = [...points, ...(box ? [box] : [])]
      const blob = await removeBackground(file, {
        model: mode === 'select' ? 'sam' : model || undefined,
        alphaMatting,
        points: mode === 'select' ? prompt : undefined,
        invert: mode === 'select' && selectionAction === 'remove',
      })
      setResultUrl((old) => {
        if (old) URL.revokeObjectURL(old)
        return URL.createObjectURL(blob)
      })
      setSlider(50)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      if (timerRef.current) clearInterval(timerRef.current)
      setBusy(false)
    }
  }

  // ---- post-result brush editor ----

  useEffect(() => {
    if (!editing || !resultUrl || !originalUrl) return
    const canvas = canvasRef.current
    if (!canvas) return
    const res = new Image()
    res.onload = () => {
      canvas.width = res.naturalWidth
      canvas.height = res.naturalHeight
      canvas.getContext('2d')?.drawImage(res, 0, 0)
    }
    res.src = resultUrl
    const orig = new Image()
    orig.src = originalUrl
    origImgRef.current = orig
    undoRef.current = []
    setUndoCount(0)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editing])

  const canvasPos = (e: { clientX: number; clientY: number }): XY => {
    const c = canvasRef.current
    if (!c) return { x: 0, y: 0 }
    const r = c.getBoundingClientRect()
    return {
      x: ((e.clientX - r.left) / r.width) * c.width,
      y: ((e.clientY - r.top) / r.height) * c.height,
    }
  }

  const stamp = (ctx: CanvasRenderingContext2D, x: number, y: number) => {
    if (editTool === 'erase') {
      ctx.globalCompositeOperation = 'destination-out'
      ctx.beginPath()
      ctx.arc(x, y, brush / 2, 0, Math.PI * 2)
      ctx.fill()
      ctx.globalCompositeOperation = 'source-over'
    } else if (origImgRef.current?.complete) {
      ctx.save()
      ctx.beginPath()
      ctx.arc(x, y, brush / 2, 0, Math.PI * 2)
      ctx.clip()
      ctx.drawImage(origImgRef.current, 0, 0, ctx.canvas.width, ctx.canvas.height)
      ctx.restore()
    }
  }

  const stampLine = (ctx: CanvasRenderingContext2D, from: XY, to: XY) => {
    const dist = Math.hypot(to.x - from.x, to.y - from.y)
    const steps = Math.max(1, Math.ceil(dist / (brush / 4)))
    for (let i = 0; i <= steps; i++) {
      stamp(ctx, from.x + ((to.x - from.x) * i) / steps, from.y + ((to.y - from.y) * i) / steps)
    }
  }

  const brushDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (e.button !== 0) return
    const canvas = canvasRef.current
    const ctx = canvas?.getContext('2d')
    if (!canvas || !ctx) return
    undoRef.current.push(ctx.getImageData(0, 0, canvas.width, canvas.height))
    if (undoRef.current.length > 8) undoRef.current.shift()
    setUndoCount(undoRef.current.length)
    e.currentTarget.setPointerCapture(e.pointerId)
    const p = canvasPos(e)
    editStrokeRef.current = p
    stamp(ctx, p.x, p.y)
  }

  const brushMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!editStrokeRef.current) return
    const ctx = canvasRef.current?.getContext('2d')
    if (!ctx) return
    const p = canvasPos(e)
    stampLine(ctx, editStrokeRef.current, p)
    editStrokeRef.current = p
  }

  const brushUp = () => {
    editStrokeRef.current = null
  }

  const undo = () => {
    const snap = undoRef.current.pop()
    const ctx = canvasRef.current?.getContext('2d')
    if (!snap || !ctx) return
    ctx.putImageData(snap, 0, 0)
    setUndoCount(undoRef.current.length)
  }

  const applyEdit = () => {
    canvasRef.current?.toBlob((blob) => {
      if (!blob) return
      setResultUrl((old) => {
        if (old) URL.revokeObjectURL(old)
        return URL.createObjectURL(blob)
      })
      setEditing(false)
    }, 'image/png')
  }

  // ---- render ----

  const busyMeta = mode === 'select' ? samMeta : models.find((m) => m.name === model)
  const editingSelection = mode === 'select' && originalUrl && !resultUrl
  const shownBox = dragBox
    ? {
        x1: Math.min(dragBox.x1, dragBox.x2),
        y1: Math.min(dragBox.y1, dragBox.y2),
        x2: Math.max(dragBox.x1, dragBox.x2),
        y2: Math.max(dragBox.y1, dragBox.y2),
      }
    : box
  const hasSelection = points.length > 0 || box !== null
  const img = imgRef.current

  return (
    <div className="page">
      <header>
        <h1>BGRemover</h1>
        <p>Remove image backgrounds locally — nothing leaves your machine.</p>
      </header>

      <div className="modes">
        <button
          className={mode === 'auto' ? 'mode active' : 'mode'}
          onClick={() => {
            setMode('auto')
            setEditing(false)
            dropResult()
          }}
          disabled={busy}
        >
          Auto — remove background
        </button>
        <button
          className={mode === 'select' ? 'mode active' : 'mode'}
          onClick={() => {
            setMode('select')
            setEditing(false)
            dropResult()
          }}
          disabled={busy}
        >
          Select object
        </button>
      </div>

      {!editing && (
        <section className="controls">
          {mode === 'auto' && (
            <label>
              Model
              <select value={model} onChange={(e) => chooseModel(e.target.value)} disabled={busy}>
                {autoModels.map((m) => (
                  <option key={m.name} value={m.name}>
                    {m.label} — {m.quality}, {m.speed}
                  </option>
                ))}
              </select>
            </label>
          )}
          {mode === 'select' && (
            <>
              <div className="toolrow">
                <button
                  className={selectTool === 'point' ? 'active' : ''}
                  onClick={() => setSelectTool('point')}
                  disabled={busy}
                  title="Click = keep point, right-click = exclude point, drag = box"
                >
                  ✛ Point / Box
                </button>
                <button
                  className={selectTool === 'brush' ? 'active' : ''}
                  onClick={() => setSelectTool('brush')}
                  disabled={busy}
                  title="Paint over the object to select it"
                >
                  🖌 Brush select
                </button>
                <span className="divider-v" />
                <label className="check">
                  <input
                    type="radio"
                    name="selaction"
                    checked={selectionAction === 'keep'}
                    onChange={() => setSelectionAction('keep')}
                    disabled={busy}
                  />
                  Keep selection
                </label>
                <label className="check">
                  <input
                    type="radio"
                    name="selaction"
                    checked={selectionAction === 'remove'}
                    onChange={() => setSelectionAction('remove')}
                    disabled={busy}
                  />
                  Remove selection
                </label>
              </div>
              <div className="selectinfo">
                <span>
                  <strong>{points.length}</strong> point{points.length === 1 ? '' : 's'}
                  {box ? ' + box' : ''}
                  {!hasSelection ? ' (nothing marked: auto-selects the center object)' : ''}
                </span>
                <button
                  onClick={() => {
                    setPoints([])
                    setBox(null)
                  }}
                  disabled={busy || !hasSelection}
                >
                  Clear
                </button>
              </div>
            </>
          )}
          <label className="check">
            <input
              type="checkbox"
              checked={alphaMatting}
              onChange={(e) => setAlphaMatting(e.target.checked)}
              disabled={busy}
            />
            Alpha matting (finer hair/fur edges, slower)
          </label>
          <button className="primary" onClick={process} disabled={!file || busy}>
            {busy
              ? `Processing… ${elapsed}s`
              : mode === 'select'
                ? selectionAction === 'remove'
                  ? 'Remove selection'
                  : hasSelection
                    ? 'Cut out selection'
                    : 'Auto select subject'
                : 'Remove background'}
          </button>
        </section>
      )}

      {editing && (
        <section className="controls">
          <div className="brushbar">
            <button
              className={editTool === 'restore' ? 'active' : ''}
              onClick={() => setEditTool('restore')}
            >
              🖌 Restore
            </button>
            <button
              className={editTool === 'erase' ? 'active' : ''}
              onClick={() => setEditTool('erase')}
            >
              ◻ Erase
            </button>
            <label className="brushsize">
              Size
              <input
                type="range"
                min="8"
                max="200"
                value={brush}
                onChange={(e) => setBrush(Number(e.target.value))}
              />
            </label>
            <button onClick={undo} disabled={undoCount === 0}>
              ↩ Undo
            </button>
            <button className="primary" onClick={applyEdit}>
              Done
            </button>
            <button onClick={() => setEditing(false)}>Cancel</button>
          </div>
          <p className="hint">
            Paint over the image: <strong>Restore</strong> brings the original back,{' '}
            <strong>Erase</strong> removes to transparent.
          </p>
        </section>
      )}

      {busy && busyMeta && elapsed > 8 && (
        <p className="hint">
          First use of “{busyMeta.label}” downloads ~{busyMeta.size_mb} MB of weights — later runs
          are much faster.
        </p>
      )}
      {error && <p className="error">{error}</p>}

      <section
        className={`dropzone ${dragOver ? 'over' : ''} ${editingSelection || editing ? 'picking' : ''}`}
        onDragOver={(e) => {
          e.preventDefault()
          setDragOver(true)
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragOver(false)
          acceptFile(e.dataTransfer.files[0])
        }}
        onClick={() => {
          if (!originalUrl) inputRef.current?.click()
        }}
      >
        {!originalUrl && <span>Drop an image here, or click to browse</span>}
        <input
          ref={inputRef}
          type="file"
          accept="image/png,image/jpeg,image/webp,image/bmp,image/tiff"
          hidden
          onChange={(e) => acceptFile(e.target.files?.[0])}
        />

        {originalUrl && !resultUrl && !editing && (
          <div className="pickwrap" onClick={(e) => e.stopPropagation()}>
            <img
              ref={imgRef}
              className="preview"
              src={originalUrl}
              alt="original"
              draggable={false}
              onPointerDown={selDown}
              onPointerMove={selMove}
              onPointerUp={selUp}
              onContextMenu={(e) => {
                if (mode === 'select') {
                  e.preventDefault()
                  setPoints((pts) => [...pts, { ...toNatural(e), label: 0 }])
                }
              }}
              style={mode === 'select' ? { cursor: 'crosshair', touchAction: 'none' } : undefined}
            />
            {mode === 'select' && img && stroke.length > 1 && (
              <svg
                className="strokelayer"
                viewBox={`0 0 ${img.naturalWidth} ${img.naturalHeight}`}
                preserveAspectRatio="none"
              >
                <polyline
                  points={stroke.map((p) => `${p.x},${p.y}`).join(' ')}
                  fill="none"
                  stroke="#00b894"
                  strokeWidth={Math.max(4, img.naturalWidth / 80)}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  opacity={0.65}
                />
              </svg>
            )}
            {mode === 'select' && img && shownBox && (
              <span
                className="selbox"
                style={{
                  left: `${(shownBox.x1 / img.naturalWidth) * 100}%`,
                  top: `${(shownBox.y1 / img.naturalHeight) * 100}%`,
                  width: `${((shownBox.x2 - shownBox.x1) / img.naturalWidth) * 100}%`,
                  height: `${((shownBox.y2 - shownBox.y1) / img.naturalHeight) * 100}%`,
                }}
              />
            )}
            {mode === 'select' &&
              img &&
              points.map((p, i) => (
                <span
                  key={`${p.x}-${p.y}-${i}`}
                  className={`marker ${p.label === 1 ? 'keep' : 'exclude'}`}
                  style={{
                    left: `${(p.x / img.naturalWidth) * 100}%`,
                    top: `${(p.y / img.naturalHeight) * 100}%`,
                  }}
                  title={p.label === 1 ? 'keep (click to remove)' : 'exclude (click to remove)'}
                  onPointerDown={(e) => e.stopPropagation()}
                  onClick={(e) => {
                    e.stopPropagation()
                    setPoints((pts) => pts.filter((_, j) => j !== i))
                  }}
                >
                  {p.label === 1 ? '+' : '−'}
                </span>
              ))}
          </div>
        )}

        {editing && (
          <div className="checker editwrap" onClick={(e) => e.stopPropagation()}>
            <canvas
              ref={canvasRef}
              className="editcanvas"
              onPointerDown={brushDown}
              onPointerMove={brushMove}
              onPointerUp={brushUp}
              onPointerLeave={brushUp}
            />
          </div>
        )}

        {originalUrl && resultUrl && !editing && (
          <div className="compare" onClick={(e) => e.stopPropagation()}>
            <div className="checker">
              <img src={resultUrl} alt="background removed" />
              <div className="overlay" style={{ clipPath: `inset(0 ${100 - slider}% 0 0)` }}>
                <img src={originalUrl} alt="original" />
              </div>
              <div className="divider" style={{ left: `${slider}%` }} />
            </div>
            <input
              className="slider"
              type="range"
              min="0"
              max="100"
              value={slider}
              onChange={(e) => setSlider(Number(e.target.value))}
            />
            <div className="labels">
              <span>Original</span>
              <span>Result</span>
            </div>
          </div>
        )}
      </section>

      {resultUrl && !editing && (
        <section className="actions">
          <a className="primary button" href={resultUrl} download="bgremover-result.png">
            Download PNG
          </a>
          <button onClick={() => setEditing(true)}>🖌 Refine with brush</button>
          {mode === 'select' && <button onClick={dropResult}>Adjust selection</button>}
          <button onClick={() => inputRef.current?.click()}>Choose another image</button>
        </section>
      )}
    </div>
  )
}
