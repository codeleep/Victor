const DEFAULTS = {
  victorBaseUrl: 'http://localhost:8080',
  victorToken: '',
  bankId: '',
  pageSize: 20,
  maxPages: 50,
  includeAnswers: true,
};

const fields = ['victorBaseUrl', 'victorToken', 'bankId', 'pageSize', 'maxPages', 'includeAnswers'];
const statusEl = document.getElementById('status');
const startBtn = document.getElementById('start');
const stopBtn = document.getElementById('stop');

function setStatus(text) {
  statusEl.textContent = text;
}

function normalizeBaseUrl(url) {
  return (url || '').trim().replace(/\/+$/, '');
}

async function getActiveTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab;
}

function bankIdFromUrl(url) {
  const match = String(url || '').match(/\/bank\/(\d+)/);
  return match ? match[1] : '';
}

async function loadOptions() {
  const saved = await chrome.storage.local.get(DEFAULTS);
  const tab = await getActiveTab();
  const currentBankId = bankIdFromUrl(tab?.url);
  for (const field of fields) {
    const el = document.getElementById(field);
    if (el.type === 'checkbox') {
      el.checked = Boolean(saved[field]);
    } else {
      el.value = field === 'bankId' && !saved[field] ? currentBankId : saved[field];
    }
  }
}

async function getVictorTokenFromOpenTabs() {
  const tabs = await chrome.tabs.query({ url: ['http://localhost:5173/*', 'http://127.0.0.1:5173/*', 'http://localhost:8080/*', 'http://127.0.0.1:8080/*'] });
  for (const tab of tabs) {
    if (!tab.id) continue;
    try {
      const [result] = await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        func: () => localStorage.getItem('token') || '',
      });
      const token = String(result?.result || '').trim();
      if (token) return token;
    } catch (_) {
      // Ignore tabs that cannot run scripts.
    }
  }
  return '';
}
async function saveOptions() {
  const options = {};
  for (const field of fields) {
    const el = document.getElementById(field);
    if (el.type === 'checkbox') {
      options[field] = el.checked;
    } else if (el.type === 'number') {
      options[field] = Number(el.value);
    } else {
      options[field] = el.value.trim();
    }
  }
  options.victorBaseUrl = normalizeBaseUrl(options.victorBaseUrl);
  await chrome.storage.local.set(options);
  return options;
}

async function sendToContent(message) {
  const tab = await getActiveTab();
  if (!tab?.id) throw new Error('找不到当前标签页');
  if (!String(tab.url || '').startsWith('https://www.mianshiya.com/')) {
    throw new Error('请先打开 https://www.mianshiya.com/ 的题库页面');
  }
  return chrome.tabs.sendMessage(tab.id, message);
}

startBtn.addEventListener('click', async () => {
  startBtn.disabled = true;
  setStatus('正在启动...');
  try {
    const options = await saveOptions();
    if (!options.bankId) throw new Error('未识别到题库 ID，请手动填写');
    if (!options.victorBaseUrl) throw new Error('请填写 Victor API 地址');
    if (!options.victorToken) throw new Error('未读取到 Victor Token，请先在浏览器打开并登录 Victor，或手动填写 Token');
    const response = await sendToContent({ type: 'START_IMPORT', options });
    setStatus(response?.message || '任务已启动，请查看页面右下角进度浮窗');
  } catch (error) {
    setStatus(`启动失败：${error.message}`);
  } finally {
    startBtn.disabled = false;
  }
});

stopBtn.addEventListener('click', async () => {
  try {
    const response = await sendToContent({ type: 'STOP_IMPORT' });
    setStatus(response?.message || '已发送停止指令');
  } catch (error) {
    setStatus(`停止失败：${error.message}`);
  }
});

chrome.runtime.onMessage.addListener((message) => {
  if (message?.type === 'IMPORT_STATUS') {
    setStatus(message.text);
  }
});

loadOptions().catch((error) => setStatus(`初始化失败：${error.message}`));
