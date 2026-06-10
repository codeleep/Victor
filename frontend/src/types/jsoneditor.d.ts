declare module 'jsoneditor' {
  export type JSONEditorMode = 'tree' | 'view' | 'form' | 'code' | 'text' | 'preview'

  export interface JSONEditorOptions {
    mode?: JSONEditorMode
    modes?: JSONEditorMode[]
    name?: string
    mainMenuBar?: boolean
    navigationBar?: boolean
    statusBar?: boolean
    search?: boolean
    language?: string
    onChange?: () => void
    onError?: (error: Error) => void
  }

  export default class JSONEditor {
    constructor(container: HTMLElement, options?: JSONEditorOptions, json?: unknown)
    set(json: unknown): void
    get(): unknown
    destroy(): void
  }
}
