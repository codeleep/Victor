import { useId } from 'react'
import { MdPreview, config } from 'md-editor-rt'
import mermaid from 'mermaid'
import 'md-editor-rt/lib/preview.css'

config({
  editorExtensions: { mermaid: { instance: mermaid, enableZoom: true } },
})

export interface MarkdownViewProps {
  source?: string
  className?: string
}

export default function MarkdownView({ source, className }: MarkdownViewProps) {
  const editorId = 'md-' + useId().replace(/[^a-zA-Z0-9]/g, '')
  return (
    <MdPreview
      editorId={editorId}
      value={source ?? ''}
      theme="light"
      previewTheme="default"
      className={className}
    />
  )
}