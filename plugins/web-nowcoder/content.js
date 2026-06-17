let stopRequested = false;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function cleanText(text) {
  return String(text || '')
    .replace(/\s+/g, ' ')
    .replace(/[ \t]+/g, ' ')
    .trim();
}

function cleanHtmlText(html) {
  return String(html || '')
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/\s+/g, ' ')
    .trim();
}

function extractExamQuestions() {
  const questions = [];
  const questionList = document.querySelector('.question-list');
  if (!questionList) return questions;

  const questionItems = questionList.querySelectorAll('.question-desc');
  let index = 0;
  for (const item of questionItems) {
    const text = cleanText(item.textContent);
    if (/^\d+[、.]/.test(text) && text.length > 5 && text.length < 500) {
      questions.push({
        index: index++,
        title: text.replace(/^\d+[、.]\s*/, '').trim(),
        element: item,
      });
    }
  }

  if (questions.length === 0) {
    const fullText = document.body.textContent;
    const match = fullText.match(/面试开始([\s\S]*?)面试结束/);
    if (match) {
      const qText = match[1];
      const regex = /(\d+)[、.](.+?)(?=\n\s*\d+[、.]|$)/g;
      let m;
      let idx = 0;
      while ((m = regex.exec(qText)) !== null) {
        const title = cleanText(m[2]);
        if (title && title.length > 5) {
          questions.push({ index: idx++, title });
        }
      }
    }
  }

  return questions;
}

function getCurrentAnswer() {
  const answerSelectors = [
    '.question-answer-wrap',
    '.answer-brief',
    '.old-nc-post-content',
    '[class*="answer-content"]',
  ];

  for (const selector of answerSelectors) {
    const el = document.querySelector(selector);
    if (el) {
      const text = cleanHtmlText(el.innerHTML);
      if (text.length > 50) {
        return text;
      }
    }
  }

  return '';
}

function getPageMeta() {
  const title = document.title;
  const url = location.href;
  const companyMatch = title.match(/(\d+年)?[—-]?(.+?)[—-]?.+面试题/);
  const company = companyMatch ? companyMatch[2] : '牛客网';

  return { title, url, company };
}

function buildPayload(question, answer, meta, options) {
  const descriptionParts = [];

  if (options.includeSource) {
    descriptionParts.push(`来源：牛客网 - ${meta.company}`);
    descriptionParts.push(`原题链接：${meta.url}`);
    descriptionParts.push(`题目来源：${meta.title}`);
  }

  return {
    title: question.title,
    description: descriptionParts.join('\n'),
    type: 'TECHNICAL',
    difficulty: 'MEDIUM',
    tags: [meta.company, '牛客网', '面试题'],
    referenceAnswer: answer,
  };
}

async function runScraping(options) {
  stopRequested = false;
  const meta = getPageMeta();

  const isExamPage = location.href.includes('/exam/interview/');
  if (!isExamPage) {
    sendStatus('请在牛客面试题页面使用本插件', true);
    return;
  }

  const questions = extractExamQuestions();
  if (questions.length === 0) {
    sendStatus('未找到题目，请确保在牛客面试题页面', true);
    return;
  }

  sendStatus(`找到 ${questions.length} 道题目，开始抓取...`);

  let imported = 0;
  let failed = 0;
  const errors = [];

  for (let i = 0; i < questions.length; i++) {
    if (stopRequested) break;

    const question = questions[i];
    sendStatus(`正在处理第 ${i + 1}/${questions.length} 题：${question.title.slice(0, 30)}...`);

    try {
      if (question.element) {
        question.element.click();
        await sleep(options.questionDelay);
      }

      const answer = getCurrentAnswer();
      const payload = buildPayload(question, answer, meta, options);

      const response = await chrome.runtime.sendMessage({
        type: 'UPLOAD_QUESTION',
        options,
        payload,
      });

      if (response?.ok) {
        imported++;
      } else {
        failed++;
        errors.push(`${i + 1}. ${question.title.slice(0, 30)}: ${response?.error || '未知错误'}`);
      }
    } catch (error) {
      failed++;
      errors.push(`${i + 1}. ${question.title.slice(0, 30)}: ${error.message}`);
    }
  }

  const summary = `抓取完成！成功上传 ${imported} 题，失败 ${failed} 题`;
  const statusText = errors.length > 0
    ? `${summary}\n\n错误详情：\n${errors.slice(0, 10).join('\n')}${errors.length > 10 ? '\n...还有更多' : ''}`
    : summary;

  sendStatus(statusText, true);
}

function sendStatus(text, done = false) {
  chrome.runtime.sendMessage({ type: 'SCRAPING_STATUS', text, done });
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'START_SCRAPING') {
    runScraping(message.options).catch((error) => {
      sendStatus(`抓取失败：${error.message}`, true);
    });
    sendResponse({ message: '已开始抓取' });
    return true;
  }

  if (message?.type === 'STOP_SCRAPING') {
    stopRequested = true;
    sendResponse({ message: '停止指令已发送' });
    return true;
  }
});
