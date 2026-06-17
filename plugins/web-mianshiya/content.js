let stopRequested = false;
let panel;

function ensurePanel() {
  if (panel) return panel;
  panel = document.createElement('div');
  panel.id = 'web-mianshiya-panel';
  panel.style.cssText = `
    position: fixed;
    right: 16px;
    bottom: 16px;
    z-index: 2147483647;
    width: 360px;
    max-height: 320px;
    overflow: auto;
    padding: 12px;
    border-radius: 10px;
    background: rgba(17, 24, 39, .94);
    color: #f9fafb;
    font: 12px/1.5 -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif;
    box-shadow: 0 12px 32px rgba(0,0,0,.3);
    white-space: pre-wrap;
  `;
  document.documentElement.appendChild(panel);
  return panel;
}

function updateStatus(text) {
  ensurePanel().textContent = text;
  chrome.runtime.sendMessage({ type: 'IMPORT_STATUS', text }).catch(() => {});
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}


async function mianshiyaPost(path, body) {
  const response = await fetch(`https://api.mianshiya.com/api/${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'content-type': 'application/json',
      'accept': 'application/json, text/plain, */*',
    },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let json;
  try { json = JSON.parse(text); } catch { throw new Error(`面试鸭响应不是 JSON: HTTP ${response.status} ${text.slice(0, 200)}`); }
  if (!response.ok || json.code !== 0) {
    throw new Error(`面试鸭接口失败 ${path}: ${json.message || response.status}`);
  }
  return json.data;
}

async function getQuestionsPage(bankId, current, pageSize) {
  return mianshiyaPost('question_bank/list_question', {
    current,
    pageSize,
    questionBankId: String(bankId),
  });
}

async function getAnswers(questionId) {
  const page = await mianshiyaPost('question_answer/list/by_question', {
    current: 1,
    pageSize: 10,
    questionId: String(questionId),
  });
  return Array.isArray(page?.records) ? page.records : [];
}

function mapDifficulty(value) {
  if (value === 1) return 'EASY';
  if (value === 5) return 'HARD';
  return 'MEDIUM';
}

function cleanText(text) {
  return String(text || '')
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function answerContent(answer) {
  if (!answer) return '';
  return answer.contentType === 1 ? String(answer.content || '').trim() : cleanText(answer.content);
}
function normalizeWhitespace(text) {
  return String(text || '').replace(/[ \t\f\v]+/g, ' ').replace(/\n\s*\n\s*\n+/g, '\n\n').trim();
}

function trimDetailNoise(text, question) {
  let value = normalizeWhitespace(text);
  const title = String(question?.title || '').trim();
  if (title) {
    const secondTitleIndex = value.indexOf(title, Math.max(1, value.indexOf(title) + title.length));
    if (secondTitleIndex > 0) value = value.slice(0, secondTitleIndex).trim();
  }
  const cutMarkers = ['点击登录查看完整内容', '分享最高赚', '催更反馈', '上一题', '下一题', '目录', '热门面试题目榜', '推荐教程'];
  for (const marker of cutMarkers) {
    const index = value.indexOf(marker);
    if (index > 0) value = value.slice(0, index).trim();
  }
  return value;
}

async function getQuestionDetail(question, bankId) {
  const response = await fetch(`https://www.mianshiya.com/bank/${bankId}/question/${question.id}`, {
    method: 'GET',
    credentials: 'include',
  });
  if (!response.ok) return '';
  const html = await response.text();
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const detail = doc.querySelector('.question-main')
    || doc.querySelector('#question-content-in-bank-client')
    || doc.querySelector('.question-content')
    || doc.querySelector('.question-detail-view');
  return trimDetailNoise(detail?.innerText || detail?.textContent || '', question);
}

function buildQuestionPayload(question, answers, bankId, detailText = '') {
  const bestAnswer = question.bestQuestionAnswer || answers[0];
  const sourceUrl = `https://www.mianshiya.com/bank/${bankId}/question/${question.id}`;
  const descriptionParts = [];
  const listContent = cleanText(question.content);
  const detailContent = trimDetailNoise(detailText, question);
  const title = String(question.title || `面试鸭题目 ${question.id}`).trim();

  if (detailContent) descriptionParts.push(detailContent);
  else if (listContent) descriptionParts.push(listContent);
  else descriptionParts.push(`题目：${title}`);
  descriptionParts.push(`来源：面试鸭题库 ${bankId}`);
  descriptionParts.push(`原题 ID：${question.id}`);
  descriptionParts.push(`原题链接：${sourceUrl}`);

  return {
    title,
    description: descriptionParts.filter(Boolean).join('\n\n'),
    type: 'TECHNICAL',
    tags: Array.isArray(question.tagList) ? question.tagList.filter(Boolean) : [],
    difficulty: mapDifficulty(question.difficulty),
    referenceAnswer: answerContent(bestAnswer),
  };
}

async function uploadQuestion(options, payload) {
  const response = await chrome.runtime.sendMessage({
    type: 'UPLOAD_QUESTION',
    options,
    payload,
  });
  if (!response?.ok) {
    throw new Error(response?.error || 'Victor 上传失败');
  }
  return response.data;
}

async function runImport(options) {
  stopRequested = false;
  const pageSize = Math.max(1, Math.min(Number(options.pageSize) || 20, 100));
  const maxPages = Math.max(1, Number(options.maxPages) || 50);
  let imported = 0;
  let failed = 0;
  let fetched = 0;

  updateStatus(`开始抓取题库 ${options.bankId}...`);
  for (let current = 1; current <= maxPages; current++) {
    if (stopRequested) break;
    const page = await getQuestionsPage(options.bankId, current, pageSize);
    const records = Array.isArray(page?.records) ? page.records : [];
    const total = Number(page?.total || 0);
    updateStatus(`第 ${current} 页：获取到 ${records.length} 题，总数 ${total}。已上传 ${imported}，失败 ${failed}`);
    if (records.length === 0) break;

    for (const question of records) {
      if (stopRequested) break;
      fetched++;
      try {
        const answers = options.includeAnswers ? await getAnswers(question.id) : [];
        const detailText = await getQuestionDetail(question, options.bankId);
        const payload = buildQuestionPayload(question, answers, options.bankId, detailText);
        await uploadQuestion(options, payload);
        imported++;
        updateStatus(`上传中... 已处理 ${fetched}/${total || '?'}，成功 ${imported}，失败 ${failed}\n当前：${payload.title}`);
        await sleep(250);
      } catch (error) {
        failed++;
        updateStatus(`上传失败 ${failed} 次：${question.title || question.id}\n${error.message}\n继续处理下一题...`);
        await sleep(500);
      }
    }

    if (total && current * pageSize >= total) break;
  }

  updateStatus(`任务结束。成功上传 ${imported} 题，失败 ${failed} 题${stopRequested ? '（已手动停止）' : ''}。`);
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'START_IMPORT') {
    runImport(message.options).catch((error) => updateStatus(`任务失败：${error.message}`));
    sendResponse({ message: '已开始抓取并上传，请查看页面右下角进度。' });
    return true;
  }
  if (message?.type === 'STOP_IMPORT') {
    stopRequested = true;
    updateStatus('正在停止，当前请求完成后退出...');
    sendResponse({ message: '停止指令已发送。' });
    return true;
  }
});
