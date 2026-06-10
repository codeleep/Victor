import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import BlankLayout from './layouts/BlankLayout'
import Login from './views/auth/Login'
import Register from './views/auth/Register'
import Dashboard from './views/dashboard/Index'
import InterviewIndex from './views/interview/Index'
import ConfigWizard from './views/interview/ConfigWizard'
import InterviewRoom from './views/interview/InterviewRoom'
import ResourceIndex from './views/resource/Index'
import QuestionList from './views/resource/QuestionList'
import ResumeList from './views/resource/ResumeList'
import ExperienceList from './views/resource/ExperienceList'
import JobList from './views/resource/JobList'
import ReportDetail from './views/report/Detail'
import SettingsIndex from './views/settings/Index'
import AiConfig from './views/settings/AiConfig'
import VoiceConfig from './views/settings/VoiceConfig'
import MetadataList from './views/settings/MetadataList'
import ApiKeyConfig from './views/settings/ApiKeyConfig'
import NotFound from './views/NotFound'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('token')
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

function PublicOnly({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('token')
  if (token) return <Navigate to="/" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes */}
        <Route path="/login" element={<PublicOnly><BlankLayout><Login /></BlankLayout></PublicOnly>} />
        <Route path="/register" element={<PublicOnly><BlankLayout><Register /></BlankLayout></PublicOnly>} />

        {/* Main layout routes */}
        <Route path="/" element={<RequireAuth><MainLayout /></RequireAuth>}>
          <Route index element={<Dashboard />} />
          <Route path="interview" element={<InterviewIndex />} />
          <Route path="interview/config" element={<ConfigWizard />} />
          <Route path="resource" element={<ResourceIndex />}>
            <Route path="questions" element={<QuestionList />} />
            <Route path="resume" element={<ResumeList />} />
            <Route path="experience" element={<ExperienceList />} />
            <Route path="jobs" element={<JobList />} />
          </Route>
          <Route path="report/:id" element={<ReportDetail />} />
          <Route path="settings" element={<SettingsIndex />}>
            <Route path="ai" element={<AiConfig />} />
            <Route path="voice" element={<VoiceConfig />} />
            <Route path="metadata" element={<MetadataList />} />
            <Route path="api-key" element={<ApiKeyConfig />} />
          </Route>
        </Route>

        {/* Interview room - blank layout */}
        <Route path="/interview/room/:id" element={<RequireAuth><BlankLayout><InterviewRoom /></BlankLayout></RequireAuth>} />

        {/* 404 */}
        <Route path="*" element={<BlankLayout><NotFound /></BlankLayout>} />
      </Routes>
    </BrowserRouter>
  )
}
