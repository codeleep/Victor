function victorApiUrl(baseUrl, path) {
  const normalizedBase = String(baseUrl || '').replace(/\/+$/, '');
  if (normalizedBase.endsWith('/api/v1')) return `${normalizedBase}${path}`;
  return `${normalizedBase}/api/v1${path}`;
}

function normalizeBearer(token) {
  const value = String(token || '').trim();
  if (!value) return '';
  return value.toLowerCase().startsWith('bearer ') ? value : `Bearer ${value}`;
}

async function uploadQuestion(options, payload) {
  const authorization = normalizeBearer(options.victorToken);
  if (!authorization) {
    throw new Error('Victor Token 为空，请先登录 Victor 或填写 Token');
  }

  const response = await fetch(victorApiUrl(options.victorBaseUrl, '/questions'), {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'authorization': authorization,
    },
    body: JSON.stringify(payload),
  });
  const text = await response.text();
  let json;
  try { json = JSON.parse(text); } catch {
    if (response.status === 403) {
      throw new Error('Victor HTTP 403：Token 无效或未发送，请重新登录 Victor 后重试');
    }
    throw new Error(`Victor 响应不是 JSON: HTTP ${response.status} ${text.slice(0, 200)}`);
  }
  if (!response.ok || (json.code !== 0 && json.code !== 200)) {
    throw new Error(`Victor 上传失败: ${json.message || response.status}`);
  }
  return json.data;
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type !== 'UPLOAD_QUESTION') return false;

  uploadQuestion(message.options, message.payload)
    .then((data) => sendResponse({ ok: true, data }))
    .catch((error) => sendResponse({ ok: false, error: error.message }));

  return true;
});
