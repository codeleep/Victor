const DEFAULTS = {
  victorBaseUrl: 'http://localhost:8080',
  authType: 'apiKey',
  victorApiKey: '',
  victorToken: '',
  questionDelay: 1500,
  includeSource: true,
};

const fields = ['victorBaseUrl', 'authType', 'victorApiKey', 'victorToken', 'questionDelay', 'includeSource'];
const statusEl = document.getElementById('status');
const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const autoFillTokenBtn = document.getElementById('autoFillToken');
const jwtTokenSection = document.getElementById('jwtTokenSection');

function setStatus(text) {
  statusEl.textContent = text;
}

function updateAuthTypeUI(authType) {
  jwtTokenSection.style.display = authType === 'jwt' ? 'block' : 'none';
}

async function getActiveTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab;
}

async function loadOptions() {
  const saved = await chrome.storage.local.get(DEFAULTS);
  for (const field of fields) {
    if (field === 'authType') {
      const radio = document.querySelector(`input[name="authType"][value="${saved[field]}"]`);
      if (radio) radio.checked = true;
      updateAuthTypeUI(saved[field]);
      continue;
    }

    const el = document.getElementById(field);
    if (!el) continue;
    if (el.type === 'checkbox') {
      el.checked = Boolean(saved[field]);
    } else {
      el.value = saved[field];
    }
  }
}

async function saveOptions() {
  const options = {};
  for (const field of fields) {
    if (field === 'authType') {
      options[field] = document.querySelector('input[name="authType"]:checked')?.value || 'apiKey';
      continue;
    }

    const el = document.getElementById(field);
    if (!el) continue;
    if (el.type === 'checkbox') {
      options[field] = el.checked;
    } else if (el.type === 'number') {
      options[field] = Number(el.value);
    } else {
      options[field] = el.value.trim();
    }
  }
  await chrome.storage.local.set(options);
  return options;
}

async function sendToContent(message) {
  const tab = await getActiveTab();
  if (!tab?.id) throw new Error('找不到当前标签页');
  if (!String(tab.url || '').includes('nowcoder.com')) {
    throw new Error('请先打开牛客网页面（面试题或面经）');
  }
  return chrome.tabs.sendMessage(tab.id, message);
}

async function autoFillVictorToken() {
  const tabs = await chrome.tabs.query({ url: ['http://localhost:8080/*', 'http://127.0.0.1:8080/*', 'http://localhost:5173/*'] });
  for (const tab of tabs) {
    if (!tab.id) continue;
    try {
      const [result] = await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        func: () => localStorage.getItem('token') || '',
      });
      const token = String(result?.result || '').trim();
      if (token) {
        document.getElementById('victorToken').value = token;
        setStatus('已从 Victor 页面自动获取 Token');
        return;
      }
    } catch (e) {
      console.log('Could not get token from tab:', e);
    }
  }
  setStatus('未找到已登录的 Victor 页面，请先登录 Victor');
}

startBtn.addEventListener('click', async () => {
  startBtn.disabled = true;
  stopBtn.disabled = false;
  setStatus('正在启动...');
  try {
    const options = await saveOptions();
    if (!options.victorBaseUrl) throw new Error('请填写 Victor 后端地址');
    if (options.authType === 'apiKey' && !options.victorApiKey) throw new Error('请填写 Victor API Key');
    if (options.authType === 'jwt' && !options.victorToken) throw new Error('请填写 Victor Token 或点击"自动获取"');
    const response = await sendToContent({ type: 'START_SCRAPING', options });
    setStatus(response?.message || '任务已启动，请查看页面控制台或进度');
  } catch (error) {
    setStatus(`启动失败：${error.message}`);
    startBtn.disabled = false;
    stopBtn.disabled = true;
  }
});

stopBtn.addEventListener('click', async () => {
  try {
    const response = await sendToContent({ type: 'STOP_SCRAPING' });
    setStatus(response?.message || '已发送停止指令');
  } catch (error) {
    setStatus(`停止失败：${error.message}`);
  } finally {
    startBtn.disabled = false;
    stopBtn.disabled = true;
  }
});

autoFillTokenBtn.addEventListener('click', (e) => {
  e.preventDefault();
  autoFillVictorToken();
});

document.querySelectorAll('input[name="authType"]').forEach((radio) => {
  radio.addEventListener('change', (event) => updateAuthTypeUI(event.target.value));
});

chrome.runtime.onMessage.addListener((message) => {
  if (message?.type === 'SCRAPING_STATUS') {
    setStatus(message.text);
    if (message.done) {
      startBtn.disabled = false;
      stopBtn.disabled = true;
    }
  }
});

loadOptions().catch((error) => setStatus(`初始化失败：${error.message}`));
