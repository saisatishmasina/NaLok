import { NavLink } from 'react-router-dom'

function HomeIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1v-9.5Z"
      />
    </svg>
  )
}

function ProjectsIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M3.5 7.5A1.5 1.5 0 0 1 5 6h4.6a1 1 0 0 1 .7.3L12 8h7a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5v-10Z"
      />
    </svg>
  )
}

function StudyIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M12 6.8C10.4 5.6 8.3 5 6 5c-1 0-2 .15-3 .45V18c1-.3 2-.45 3-.45 2.3 0 4.4.6 6 1.85 1.6-1.25 3.7-1.85 6-1.85 1 0 2 .15 3 .45V5.45C19 5.15 18 5 17 5c-2.3 0-4.4.6-5 1.8Zm0 0v12.6"
      />
    </svg>
  )
}

const items = [
  { to: '/', label: 'Home', end: true, icon: <HomeIcon /> },
  { to: '/projects', label: 'Projects', end: false, icon: <ProjectsIcon /> },
  { to: '/study', label: 'Study', end: false, icon: <StudyIcon /> },
]

export default function Sidebar() {
  return (
    <nav className="sidebar">
      <div className="brand">
        <span className="brand-mark" />
        <span className="brand-name">NaLok</span>
      </div>
      <span className="section-label">Spaces</span>
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
        >
          {item.icon}
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
