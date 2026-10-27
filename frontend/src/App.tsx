import { Navigate, Route, Routes } from 'react-router-dom'
import Sidebar from './components/Sidebar'
import HomePage from './pages/Home'
import ProjectsPage from './pages/Projects'
import ProjectDetailPage from './pages/ProjectDetail'
import StudyPage from './pages/Study'
import SectionWorkbenchPage from './pages/SectionWorkbench'
import './index.css'

export default function App() {
  return (
    <>
      <div className="ambient" aria-hidden="true" />
      <div className="app-shell">
        <Sidebar />
        <main className="content">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/projects" element={<ProjectsPage />} />
            <Route path="/projects/:id" element={<ProjectDetailPage />} />
            <Route
              path="/projects/:projectId/sections/:sectionId"
              element={<SectionWorkbenchPage />}
            />
            <Route path="/study" element={<StudyPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
    </>
  )
}
