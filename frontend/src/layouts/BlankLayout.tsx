import React from 'react'

interface BlankLayoutProps {
  children: React.ReactNode
}

export default function BlankLayout({ children }: BlankLayoutProps) {
  return <>{children}</>
}
