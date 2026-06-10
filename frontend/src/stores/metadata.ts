import { create } from 'zustand'
import { metadataApi } from '@/api'
import type { MetadataVO } from '@/types'

interface MetadataState {
  metadataMap: Map<string, MetadataVO[]>
  categories: string[]
  loaded: boolean
  getByCategory: (category: string) => MetadataVO[]
  loadByCategory: (category: string) => Promise<MetadataVO[]>
  getNameByCode: (category: string, code: string) => string
  getByCode: (category: string, code: string) => MetadataVO | undefined
  getOptions: (category: string) => { label: string; value: string }[]
  loadCategories: () => Promise<string[]>
  preloadCommon: () => Promise<void>
  refreshCategory: (category: string) => Promise<void>
  clearCache: () => void
}

export const useMetadataStore = create<MetadataState>((set, get) => ({
  metadataMap: new Map(),
  categories: [],
  loaded: false,

  getByCategory: (category: string) => {
    return get().metadataMap.get(category) || []
  },

  loadByCategory: async (category: string) => {
    const map = get().metadataMap
    if (map.has(category)) {
      return map.get(category) || []
    }
    const data = await metadataApi.list(category)
    const newMap = new Map(map)
    newMap.set(category, data)
    set({ metadataMap: newMap })
    return data
  },

  getNameByCode: (category: string, code: string) => {
    const list = get().metadataMap.get(category) || []
    const item = list.find(m => m.code === code)
    return item?.name || code
  },

  getByCode: (category: string, code: string) => {
    const list = get().metadataMap.get(category) || []
    return list.find(m => m.code === code)
  },

  getOptions: (category: string) => {
    const list = get().metadataMap.get(category) || []
    return list.map(item => ({
      label: item.name,
      value: item.code
    }))
  },

  loadCategories: async () => {
    if (get().categories.length > 0) return get().categories
    const categories = await metadataApi.getCategories()
    set({ categories })
    return categories
  },

  preloadCommon: async () => {
    if (get().loaded) return
    try {
      await get().loadCategories()
      const commonCategories = [
        'QUESTION_TYPE', 'DIFFICULTY', 'INTERVIEW_MODE', 'INTERVIEW_TYPE',
        'INTERVIEW_STATUS', 'AGENT_TYPE', 'USER_STATUS', 'RESUME_STATUS',
        'DOCUMENT_STATUS', 'PLUGIN_STATUS'
      ]
      await Promise.all(commonCategories.map(cat => get().loadByCategory(cat)))
      set({ loaded: true })
    } catch (error) {
      console.error('Failed to preload metadata:', error)
    }
  },

  refreshCategory: async (category: string) => {
    const data = await metadataApi.list(category)
    const newMap = new Map(get().metadataMap)
    newMap.set(category, data)
    set({ metadataMap: newMap })
  },

  clearCache: () => {
    set({ metadataMap: new Map(), categories: [], loaded: false })
  }
}))
