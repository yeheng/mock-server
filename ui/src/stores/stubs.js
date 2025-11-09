import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { handleApiError } from '@/lib/errorHandler'

export const useStubsStore = defineStore('stubs', () => {
  const stubs = ref([])
  const loading = ref(false)
  const currentPage = ref(1)
  const pageSize = ref(10)
  const totalElements = ref(0)
  const searchKeyword = ref('')
  const selectedStubs = ref(new Set())

  // API基础URL
  const API_BASE = '/admin/stubs'

  // 计算属性
  const totalPages = computed(() => Math.ceil(totalElements.value / pageSize.value))
  const hasNextPage = computed(() => currentPage.value < totalPages.value)
  const hasPreviousPage = computed(() => currentPage.value > 1)

  // 获取所有stubs
  const fetchStubs = async (page = 1, size = 10, keyword = '') => {
    loading.value = true
    try {
      const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
        ...(keyword && { keyword }),
      })

      const response = await fetch(`${API_BASE}/page?${params}`)

      if (!response.ok) {
        // 使用错误处理工具
        throw response
      }

      const data = await response.json()
      stubs.value = data.content
      totalElements.value = data.totalElements
      currentPage.value = data.number + 1
      pageSize.value = data.size
    } catch (error) {
      // 使用统一的错误处理
      const errorInfo = handleApiError(error)
      // 可以在这里添加用户通知逻辑
      // 例如: showNotification(errorInfo)
      throw errorInfo
    } finally {
      loading.value = false
    }
  }

  // 搜索stubs
  const searchStubs = async (keyword) => {
    searchKeyword.value = keyword
    await fetchStubs(1, pageSize.value, keyword)
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

  // 创建stub
  const createStub = async (stubData) => {
    try {
      const response = await fetch(API_BASE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stubData),
      })

      if (response.ok) {
        const newStub = await response.json()
        await fetchStubs(currentPage.value, pageSize.value, searchKeyword.value)
        return newStub
      }
      throw new Error('Failed to create stub')
    } catch (error) {
      console.error('Error creating stub:', error)
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
        await fetchStubs(currentPage.value, pageSize.value, searchKeyword.value)
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
        await fetchStubs(currentPage.value, pageSize.value, searchKeyword.value)
        return true
      }
      throw new Error('Failed to delete stub')
    } catch (error) {
      console.error(`Error deleting stub ${id}:`, error)
      throw error
    }
  }

  // 切换stub启用状态
  const toggleStub = async (id) => {
    try {
      const response = await fetch(`${API_BASE}/${id}/toggle`, {
        method: 'POST',
      })

      if (response.ok) {
        const updatedStub = await response.json()
        const index = stubs.value.findIndex((s) => s.id === id)
        if (index !== -1) {
          stubs.value[index] = updatedStub
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
        await fetchStubs(currentPage.value, pageSize.value, searchKeyword.value)
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

  // 批量删除
  const batchDeleteStubs = async (ids) => {
    const promises = ids.map((id) => deleteStub(id))
    try {
      await Promise.all(promises)
      return true
    } catch (error) {
      console.error('Error batch deleting stubs:', error)
      throw error
    }
  }

  // 批量切换状态
  const batchToggleStubs = async (ids, enable = true) => {
    const promises = ids.map(async (id) => {
      const stub = stubs.value.find((s) => s.id === id)
      if (stub && stub.enabled !== enable) {
        return toggleStub(id)
      }
    })
    try {
      await Promise.all(promises)
      return true
    } catch (error) {
      console.error('Error batch toggling stubs:', error)
      throw error
    }
  }

  // 选择操作
  const selectStub = (id) => {
    selectedStubs.value.add(id)
  }

  const deselectStub = (id) => {
    selectedStubs.value.delete(id)
  }

  const clearSelection = () => {
    selectedStubs.value.clear()
  }

  const selectAllVisible = () => {
    stubs.value.forEach((stub) => {
      selectedStubs.value.add(stub.id)
    })
  }

  const isSelected = (id) => selectedStubs.value.has(id)

  return {
    // state
    stubs,
    loading,
    currentPage,
    pageSize,
    totalElements,
    searchKeyword,
    selectedStubs,

    // computed
    totalPages,
    hasNextPage,
    hasPreviousPage,

    // actions
    fetchStubs,
    searchStubs,
    getStubById,
    createStub,
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
