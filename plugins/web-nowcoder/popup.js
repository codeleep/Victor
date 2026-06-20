const DEFAULTS = {
  victorBaseUrl: 'http://localhost:8080',
  victorApiKey: '',
  questionDelay: 300,
  includeSource: true,
}

const statusEl = document.getElementById('status')
const startBtn = document.getElementById('startBtn')
const stopBtn = document.getElementById('stopBtn')
let stopRequested = false

startBtn.addEventListener('click', async () => {
  startBtn.disabled = true
  stopBtn.disabled = false
  stopRequested = false
  setStatus('正在解析...')

  try {
    const options = await getOptions()
    if (!options.victorBaseUrl) throw new Error('请填写 Victor 后端地址')
    if (!options.victorApiKey) throw new Error('请填写 Victor API Key')

    const jsonStr = document.getElementById('questionData').value.trim()
    if (!jsonStr) throw new Error('请先粘贴题目数据 JSON')

    const data = JSON.parse(jsonStr)
    const questions = findQuestionList(data)

    if (!questions || questions.length === 0) {
      throw new Error('未找到题目数据，请确认粘贴的是 window.__INITIAL_STATE__ 的内容')
    }

    setStatus(`解析成功，共 ${questions.length} 道题，开始上传...`)
    await uploadAll(questions, options)

  } catch (error) {
    setStatus(`失败: ${error.message}`)
    startBtn.disabled = false
    stopBtn.disabled = true
  }
})

stopBtn.addEventListener('click', () => {
  stopRequested = true
  setStatus('已发送停止指令')
  startBtn.disabled = false
  stopBtn.disabled = true
})

async function getOptions() {
  const saved = await chrome.storage.local.get(DEFAULTS)
  return {
    victorBaseUrl: document.getElementById('victorBaseUrl').value || saved.victorBaseUrl,
    victorApiKey: document.getElementById('victorApiKey').value || saved.victorApiKey,
    includeSource: document.getElementById('includeSource').checked,
    questionDelay: 300,
  }
}

function findQuestionList(obj) {
  if (!obj || typeof obj !== 'object') return null

  const visited = new WeakSet()
  let result = null

  function search(o) {
    if (!o || typeof o !== 'object' || visited.has(o)) return
    visited.add(o)

    if (Array.isArray(o) && o.length > 0 && o.some(x => x && (x.title || x.content || x.questionTitle || x.referenceAnswer))) {
      result = o
      return
    }

    for (const [k, v] of Object.entries(o)) {
      if (k === 'questionList' && Array.isArray(v) && v.length > 0) {
        result = v
        return
      }
      if (v && typeof v === 'object') {
        search(v)
        if (result) return
      }
    }
  }

  search(obj)
  return result
}

async function uploadAll(questions, options) {
  let imported = 0
  let failed = 0
  const errors = []

  for (let i = 0; i < questions.length; i++) {
    if (stopRequested) break

    const q = questions[i]
    setStatus(`正在上传第 ${i + 1}/${questions.length} 题...`)

    try {
      const payload = buildPayload(q, options, i)
      const response = await uploadOne(options, payload)
      if (response?.ok) {
        imported++
      } else {
        failed++
        errors.push(`${i + 1}. ${response?.error || '未知错误'}`)
      }

      if (i < questions.length - 1) {
        await sleep(options.questionDelay)
      }
    } catch (error) {
      failed++
      errors.push(`${i + 1}. ${error.message}`)
    }
  }

  const summary = `完成！成功上传 ${imported} 题，失败 ${failed} 题`
  const statusText = errors.length > 0
    ? `${summary}\n\n错误详情：\n${errors.slice(0, 10).join('\n')}${errors.length > 10 ? '\n...还有更多' : ''}`
    : summary

  setStatus(statusText)
  startBtn.disabled = false
  stopBtn.disabled = true
}

function buildPayload(q, options, index) {
  const title = q.title || q.questionTitle || q.content?.slice(0, 100) || `题目 ${index + 1}`
  const content = q.content || q.questionContent || q.questionDesc || title
  const answer = q.referenceAnswer || q.answer || q.solution || q.explanation || ''

  const descriptionParts = []
  if (options.includeSource) {
    descriptionParts.push('来源：牛客网')
  }
  descriptionParts.push(content)

  return {
    title: title.slice(0, 200),
    description: descriptionParts.join('\n'),
    type: 'TECHNICAL',
    difficulty: 'MEDIUM',
    tags: ['牛客网', '面试题'],
    referenceAnswer: answer,
  }
}

async function uploadOne(options, payload) {
  const baseUrl = options.victorBaseUrl.replace(/\/+$/, '')
  const url = `${baseUrl}/api/v1/questions`

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-api-key': options.victorApiKey,
    },
    body: JSON.stringify(payload),
  })

  const text = await res.text()
  let json
  try {
    json = JSON.parse(text)
  } catch {
    throw new Error(`HTTP ${res.status}: ${text.slice(0, 200)}`)
  }

  if (json.code !== 200 && json.code !== 0) {
    throw new Error(json.message || `HTTP ${res.status}`)
  }

  return { ok: true, data: json.data }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function setStatus(text) {
  statusEl.textContent = text
}

async function init() {
  const saved = await chrome.storage.local.get(DEFAULTS)
  document.getElementById('victorBaseUrl').value = saved.victorBaseUrl
  document.getElementById('victorApiKey').value = saved.victorApiKey
  document.getElementById('includeSource').checked = saved.includeSource

  // 自动保存输入的内容
  Array.from(document.querySelectorAll('input, textarea')).forEach(el => {
    el.addEventListener('change', async () => {
      const options = {
        victorBaseUrl: document.getElementById('victorBaseUrl').value,
        victorApiKey: document.getElementById('victorApiKey').value,
        includeSource: document.getElementById('includeSource').checked,
      }
      await chrome.storage.local.set(options)
    })
  })
}

init().catch(e => console.error('初始化失败:', e))
