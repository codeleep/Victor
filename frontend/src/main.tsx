import React from 'react'
import ReactDOM from 'react-dom/client'
import { ConfigProvider, App as AntApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App'
import { useMetadataStore } from './stores/metadata'

import './styles/global.scss'

// Preload common metadata
const metadataStore = useMetadataStore.getState()
metadataStore.preloadCommon()

const theme = {
  token: {
    colorPrimary: '#D97757',
    colorSuccess: '#4A9E6E',
    colorWarning: '#D4A843',
    colorError: '#C45A4A',
    colorText: '#141413',
    colorTextSecondary: '#5A5A58',
    borderRadius: 8,
    fontFamily: "'Poppins', 'Noto Sans SC', sans-serif",
  },
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={theme}>
      <AntApp>
        <App />
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>
)
