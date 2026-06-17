function normalizeBearer(token) {
  const value = String(token || '').trim();
  if (!value) return '';
  return value.toLowerCase().startsWith('bearer ') ? value : `Bearer ${value}`;
}

function buildAuthHeaders(options) {
  const headers = {
    'content-type': 'application/json',
  };

  if (options.authType === 'apiKey') {
    const apiKey = String(options.victorApiKey || '').trim();
    if (!apiKey) throw new Error('Victor API Key 为空');
    headers['x-api-key'] = apiKey;
    return headers;
  }

  const authorization = normalizeBearer(options.victorToken);
  if (!authorization) throw new Error('Victor Token 为空');
  headers.authorization = authorization;
  return headers;
}

async function uploadQuestion(options, payload) {
  const baseUrl = options.victorBaseUrl.replace(/\/+$/, '');
  const url = `${baseUrl}/api/v1/questions`;

  const response = await fetch(url, {
    method: 'POST',
    headers: buildAuthHeaders(options),
    body: JSON.stringify(payload),
  });

  const text = await response.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    if (response.status === 401 || response.status === 403) {
      throw new Error(`Victor HTTP ${response.status}：认证失败，请检查 API Key 或 Token`);
    }
    throw new Error(`Victor 响应不是 JSON: HTTP ${response.status} ${text.slice(0, 200)}`);
  }

  if (!response.ok || (json.code !== 0 && json.code !== 200)) {
    throw new Error(`Victor 上传失败: ${json.message || response.status}`);
  }

  return json.data;
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'UPLOAD_QUESTION') {
    uploadQuestion(message.options, message.payload)
      .then((data) => sendResponse({ ok: true, data }))
      .catch((error) => sendResponse({ ok: false, error: error.message }));
    return true;
  }
});
