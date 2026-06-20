// 牛客题目抓取 - 解决 CSP 限制
let stopRequested = false

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'START_SCRAPING') {
    handleStartScraping(message.options)
    sendResponse({ message: '已开始抓取' })
    return true
  }
})

async function handleStartScraping(options) {
  console.log('开始抓取...')
  try {
    // 直接在 content script 里遍历 DOM 提取题目
    const result = extractFromDOM()

    if (result.questions.length > 0) {
      console.log(`DOM 提取到 ${result.questions.length} 道题目`)
      await uploadAllQuestions(result.questions, result.meta, options)
    } else {
      console.log('DOM 提取失败，尝试从网络请求捕获...')
      // TODO: 可以在这里实现监听 fetch/XHR 的逻辑
    }
  } catch (error) {
    console.error('抓取失败:', error)
  }
}

function extractFromDOM() {
  const meta = {
    company: '牛客网',
    url: location.href,
    title: document.title,
  }

  const questions = []

  // 方式1: 查找题目列表容器
  const listItems = document.querySelectorAll('[class*="question-item"], [class*="item-question"], [class*="question-desc"], li')
  for (const item of listItems) {
    const text = item.textContent?.trim()
    if (text && text.length > 10 && text.length < 200) {
      // 过滤纯数字和明显不是题目的
      if (!/^\d+$/.test(text.replace(/\s+/g, '')) && !text.includes('登录') && !text.includes('注册')) {
        questions.push({
          title: text.slice(0, 200),
          content: text,
          referenceAnswer: '',
        })
      }
    }
  }

  // 方式2: 如果题目列表不够，尝试找答题区域
  if (questions.length < 5) {
    const answerBlocks = document.querySelectorAll('[class*="answer"], [class*="解析"], [class*="reference"]')
    for (const block of answerBlocks) {
      const text = block.textContent?.trim()
      if (text && text.length > 50) {
        // 尝试找附近的题目
        const titleEl = block.previousElementSibling
        const title = titleEl?.textContent?.trim().slice(0, 100) || '题目'
        questions.push({
          title,
          content: title,
          referenceAnswer: text,
        })
      }
    }
  }

  return { meta, questions }
}

async function uploadAllQuestions(questions, meta, options) {
  console.log('准备上传:', questions.length, '题')
  // TODO: 实际上传逻辑
}
