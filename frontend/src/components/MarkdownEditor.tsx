import { useId } from 'react'
import { MdEditor } from 'md-editor-rt'
import 'md-editor-rt/lib/style.css'

export interface MarkdownEditorProps {
  value?: string
  onChange?: (value: string) => void
  height?: number | string
  placeholder?: string
  preview?: boolean
}

export default function MarkdownEditor({
  value,
  onChange,
  height = 300,
  placeholder,
  preview = true,
}: MarkdownEditorProps) {
  const editorId = 'mde-' + useId().replace(/[^a-zA-Z0-9]/g, '')
  return (
    <MdEditor
      editorId={editorId}
      value={value ?? ''}
      onChange={onChange}
      theme="light"
      preview={preview}
      placeholder={placeholder}
      style={{ height }}
    />
  )
}