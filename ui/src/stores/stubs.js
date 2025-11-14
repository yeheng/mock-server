import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { handleApiError } from '@/lib/errorHandler'

export const useStubsStore = defineStore('stubs', () => {
  // 统一状态管理 - 替代分散的多个ref
  const state = ref({
    stubs: [],
    pagination: {
      current: 0,
      size: 10,
      total: 0
    },
    searchKeyword: '',
    selectedStubs: new Set(),
    loading: false,
    error: null
  })

  // API基础URL
  const API_BASE = '/admin/stubs'

  // 计算属性 - 直接从state中获取
  const totalPages = computed(() => Math.ceil(state.value.pagination.total / state.value.pagination.size))
  const hasNextPage = computed(() => state.value.pagination.current < totalPages.value)
  const hasPreviousPage = computed(() => state.value.pagination.current > 1)
  const allVisibleSelected = computed(
    () =>
      state.value.stubs.length > 0 &&
      state.value.stubs.every((stub) => state.value.selectedStubs.has(stub.id))
  )

  // 获取所有stubs
  const fetchStubs = async (page = 0, size = 10, keyword = '') => {
    state.value.loading = true
    state.value.error = null
    try {
      const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
        ...(keyword && { keyword }),
      })

      const response = await fetch(`${API_BASE}/page?${params}`)

      if (!response.ok) {
        throw response
      }

      const data = await response.json()
      // 使用单一状态更新
      state.value.stubs = data.content
      state.value.pagination = {
        current: data.number,
        size: data.size,
        total: data.totalElements
      }
      state.value.searchKeyword = keyword
    } catch (error) {
      state.value.error = handleApiError(error)
      throw state.value.error
    } finally {
      state.value.loading = false
    }
  }

  // 搜索stubs
  const searchStubs = async (keyword) => {
    await fetchStubs(0, state.value.pagination.size, keyword)
  }

  // 获取单个stub
  const getStubById = async (id) => {
    try {
      const response = await fetch(`${API_BASE}/${id}`)
      if (response.ok) {
        return await response.json()
      }
    } catch (error) {
      console.error(`Error fetching stub ${id}:`, error)
    }
    return null
  }

  // 创建单个stub
  const createStub = async (stubData) => {
    try {
      const response = await fetch(API_BASE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stubData),
      })

      if (response.ok) {
        const newStub = await response.json()
        // 重新加载当前页面
        await fetchStubs(state.value.pagination.current, state.value.pagination.size, state.value.searchKeyword)
        return newStub
      }
      throw new Error('Failed to create stub')
    } catch (error) {
      console.error('Error creating stub:', error)
      throw error
    }
  }

  // 批量创建stubs - 使用标准化的StubMapping格式
  const createStubs = async (stubDataList) => {
    try {
      const response = await fetch(`${API_BASE}/bulk`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stubDataList),
      })

      if (response.ok) {
        const newStubs = await response.json()
        // 重新加载当前页面
        await fetchStubs(state.value.pagination.current, state.value.pagination.size, state.value.searchKeyword)
        return newStubs
      }
      throw new Error('Failed to create stubs in bulk')
    } catch (error) {
      console.error('Error creating stubs in bulk:', error)
      throw error
    }
  }

  // 批量导入stubs - 使用新的/import端点，支持原始JSON格式
  const importStubs = async (importData) => {
    try {
      const response = await fetch(`${API_BASE}/bulk/import`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(importData),
      })

      if (response.ok) {
        const newStubs = await response.json()
        // 重新加载当前页面
        await fetchStubs(state.value.pagination.current, state.value.pagination.size, state.value.searchKeyword)
        return newStubs
      }
      throw new Error('Failed to import stubs')
    } catch (error) {
      console.error('Error importing stubs:', error)
      throw error
    }
  }

  // 更新stub
  const updateStub = async (id, stubData) => {
    try {
      const response = await fetch(`${API_BASE}/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stubData),
      })

      if (response.ok) {
        const updatedStub = await response.json()
        // 重新加载当前页面
        await fetchStubs(state.value.pagination.current, state.value.pagination.size, state.value.searchKeyword)
        return updatedStub
      }
      throw new Error('Failed to update stub')
    } catch (error) {
      console.error(`Error updating stub ${id}:`, error)
      throw error
    }
  }

  // 删除stub
  const deleteStub = async (id) => {
    try {
      const response = await fetch(`${API_BASE}/${id}`, {
        method: 'DELETE',
      })

      if (response.ok) {
        // 重新加载当前页面
        await fetchStubs(state.value.pagination.current, state.value.pagination.size, state.value.searchKeyword)
        return true
      }
      throw new Error('Failed to delete stub')
    } catch (error) {
      console.error(`Error deleting stub ${id}:`, error)
      throw error
    }
  }

  // 切换stub启用状态 - 优化为单条更新，不刷新全量
  const toggleStub = async (id) => {
    try {
      const response = await fetch(`${API_BASE}/${id}/toggle`, {
        method: 'POST',
      })

      if (response.ok) {
        const updatedStub = await response.json()
        // 直接更新本地状态，避免全量刷新
        const index = state.value.stubs.findIndex((s) => s.id === id)
        if (index !== -1) {
          state.value.stubs[index] = updatedStub
        }
        return updatedStub
      }
      throw new Error('Failed to toggle stub')
    } catch (error) {
      console.error(`Error toggling stub ${id}:`, error)
      throw error
    }
  }

  // 重新加载所有stubs
  const reloadAllStubs = async () => {
    try {
      const response = await fetch(`${API_BASE}/reload`, {
        method: 'POST',
      })

      if (response.ok) {
        // 重新加载当前页面
        await fetchStubs(state.value.pagination.current, state.value.pagination.size, state.value.searchKeyword)
        return true
      }
      throw new Error('Failed to reload stubs')
    } catch (error) {
      console.error('Error reloading stubs:', error)
      throw error
    }
  }

  // 获取统计信息
  const getStatistics = async () => {
    try {
      const response = await fetch(`${API_BASE}/statistics`)
      if (response.ok) {
        return await response.json()
      }
    } catch (error) {
      console.error('Error fetching statistics:', error)
    }
    return null
  }

  // 批量删除 - 添加事务性支持
  const batchDeleteStubs = async (ids) => {
    const results = []
    const errors = []
    // 改为顺序执行以支持部分失败处理
    for (const id of ids) {
      try {
        const result = await deleteStub(id)
        results.push({ id, success: true, result })
      } catch (error) {
        errors.push({ id, success: false, error })
      }
    }
    // 如果有错误，抛出包含详细信息的错误
    if (errors.length > 0) {
      const errorMsg = `${errors.length} of ${ids.length} operations failed`
      console.error('Error batch deleting stubs:', errorMsg, errors)
      throw new Error(errorMsg)
    }
    return true
  }

  // 批量切换状态 - 添加事务性支持
  const batchToggleStubs = async (ids, enable = true) => {
    const results = []
    const errors = []
    // 改为顺序执行以支持部分失败处理
    for (const id of ids) {
      try {
        const stub = state.value.stubs.find((s) => s.id === id)
        if (stub && stub.enabled !== enable) {
          const result = await toggleStub(id)
          results.push({ id, success: true, result })
        } else {
          results.push({ id, success: true, skipped: true })
        }
      } catch (error) {
        errors.push({ id, success: false, error })
      }
    }
    // 如果有错误，抛出包含详细信息的错误
    if (errors.length > 0) {
      const errorMsg = `${errors.length} of ${ids.length} operations failed`
      console.error('Error batch toggling stubs:', errorMsg, errors)
      throw new Error(errorMsg)
    }
    return true
  }

  // 选择操作 - 使用统一状态
  const selectStub = (id) => {
    state.value.selectedStubs.add(id)
  }

  const deselectStub = (id) => {
    state.value.selectedStubs.delete(id)
  }

  const clearSelection = () => {
    state.value.selectedStubs.clear()
  }

  const selectAllVisible = () => {
    state.value.stubs.forEach((stub) => {
      state.value.selectedStubs.add(stub.id)
    })
  }

  const isSelected = (id) => state.value.selectedStubs.has(id)

  // 返回统一状态和计算属性
  return {
    // state
    state,
    allVisibleSelected,

    // computed
    totalPages,
    hasNextPage,
    hasPreviousPage,

    // actions
    fetchStubs,
    searchStubs,
    getStubById,
    createStub,
    createStubs,
    importStubs,
    updateStub,
    deleteStub,
    toggleStub,
    reloadAllStubs,
    getStatistics,
    batchDeleteStubs,
    batchToggleStubs,
    selectStub,
    deselectStub,
    clearSelection,
    selectAllVisible,
    isSelected,
  }
})
