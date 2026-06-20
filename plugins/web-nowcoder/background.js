// 牛客题目抓取 - 后台服务入口
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'UPLOAD_QUESTION') {
    uploadQuestion(message.options, message.payload)
      .then((data) => sendResponse({ ok: true, data }))
      .catch((error) => sendResponse({ ok: false, error: error.message }))
    return true
  }
})

async function uploadQuestion(options, payload) {
  const baseUrl = (options.victorBaseUrl || 'http://localhost:8080').replace(/\/+$/, '')
  const url = `${baseUrl}/api/v1/questions`

  const headers = {
    'content-type': 'application/json',
  }

  if (options.authType === 'apiKey' && options.victorApiKey) {
    headers['X-API-Key'] = options.victorApiKey
  } else if (options.victorToken) {
    headers['authorization'] = `Bearer ${options.victorToken}`
  } else {
    throw new Error('需要配置 API Key 或 Token')
  }

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 200)}`)
  }

  const result = await response.json()
  if (result.code !== 200 && result.code !== 0) {
    throw new Error(result.message || '上传失败')
  }

  return result.data
}
