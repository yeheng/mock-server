<script setup>
import { ref } from 'vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
const stubsStore = useStubsStore()
const isLoading = ref(false)

// 导入 stub - 移除alert()，使用统一的错误处理
const handleImport = async (event) => {
  const file = event.target.files[0]
  if (!file) return

  if (!file.name.endsWith('.json')) {
    console.error('只支持 JSON 文件')
    return
  }

  isLoading.value = true

  try {
    const text = await file.text()
    const data = JSON.parse(text)

    let importedCount = 0
    let skippedCount = 0

    // 支持多种格式：单个 stub、stub 数组、WireMock 格式
    let importData = {}

    if (Array.isArray(data)) {
      // 直接是 stub 数组 - 使用 stubs 字段包装
      importData = { stubs: data }
    } else if (data.mappings) {
      // WireMock 格式：{ "mappings": [...] }
      importData = { mappings: data.mappings }
    } else if (data.request || data.response) {
      // 单个 stub 对象 - 使用 stubs 字段包装为数组
      importData = { stubs: [data] }
    } else {
      throw new Error('不支持的 JSON 格式')
    }

    // 使用新的导入 API - 后端会处理标准化和去重
    try {
      const createdStubs = await stubsStore.importStubs(importData)
      importedCount = createdStubs.length
      console.log(`导入完成: 成功导入 ${importedCount} 个 stub`)
    } catch (error) {
      console.error('Failed to import stubs:', error)
      throw error
    }

  } catch (error) {
    console.error('Import failed:', error)
    // 不在这里显示错误，由调用方处理
    throw error
  } finally {
    isLoading.value = false
    // 清空 file input
    event.target.value = ''
  }
}

// 导出 stub - 移除alert()
const handleExport = async () => {
  if (stubsStore.state.value.stubs.length === 0) {
    console.warn('没有 stub 可导出')
    return
  }

  isLoading.value = true

  try {
    // 准备导出数据（WireMock 兼容格式）
    const exportData = {
      mappings: stubsStore.state.value.stubs.map(stub => ({
        id: stub.id,
        name: stub.name,
        request: stub.request,
        response: stub.response,
        priority: stub.priority,
        enabled: stub.enabled
      }))
    }

    // 创建下载
    const blob = new Blob([JSON.stringify(exportData, null, 2)], {
      type: 'application/json'
    })
    const url = URL.createObjectURL(blob)

    const a = document.createElement('a')
    a.href = url
    a.download = `wiremock-stubs-${new Date().toISOString().split('T')[0]}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    console.log(`导出成功: 已导出 ${stubsStore.state.value.stubs.length} 个 stub`)
  } catch (error) {
    console.error('Export failed:', error)
    // 不显示alert，错误由调用方处理
    throw error
  } finally {
    isLoading.value = false
  }
}

// 导出选中的 stub - 移除toast()调用
const handleExportSelected = async (selectedStubs) => {
  if (selectedStubs.length === 0) {
    console.warn('请先选择要导出的 stub')
    return
  }

  isLoading.value = true

  try {
    const exportData = {
      mappings: selectedStubs.map(stub => ({
        id: stub.id,
        name: stub.name,
        request: stub.request,
        response: stub.response,
        priority: stub.priority,
        enabled: stub.enabled
      }))
    }

    const blob = new Blob([JSON.stringify(exportData, null, 2)], {
      type: 'application/json'
    })
    const url = URL.createObjectURL(blob)

    const a = document.createElement('a')
    a.href = url
    a.download = `wiremock-selected-stubs-${new Date().toISOString().split('T')[0]}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    console.log(`已导出 ${selectedStubs.length} 个选中的 stub`)

  } catch (error) {
    console.error('Export selected failed:', error)
    console.error('导出失败:', error.message)
    // 不显示toast，错误由调用方处理
    throw error
  } finally {
    isLoading.value = false
  }
}

// 标准化逻辑已移至后端 StubMappingController
// 前端现在直接传递原始数据，不进行转换

// 导出单个 stub
const exportSingleStub = (stub) => {
  const exportData = {
    id: stub.id,
    name: stub.name,
    request: stub.request,
    response: stub.response,
    priority: stub.priority,
    enabled: stub.enabled
  }
  
  const blob = new Blob([JSON.stringify(exportData, null, 2)], { 
    type: 'application/json' 
  })
  const url = URL.createObjectURL(blob)
  
  const a = document.createElement('a')
  a.href = url
  a.download = `stub-${stub.name.replace(/[^a-z0-9]/gi, '-').toLowerCase()}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

defineExpose({
  handleImport,
  handleExport,
  handleExportSelected,
  exportSingleStub
})
</script>

<template>
  <div class="space-y-2">
    <!-- 导入 -->
    <div class="flex items-center space-x-2">
      <input
        type="file"
        accept=".json"
        @change="handleImport"
        class="hidden"
        id="import-file"
        :disabled="isLoading"
      />
      <Button 
        variant="outline" 
        size="sm" 
        @click="document.getElementById('import-file').click()"
        :disabled="isLoading"
      >
        {{ isLoading ? '处理中...' : '导入 JSON' }}
      </Button>
    </div>
    
    <!-- 导出 -->
    <Button 
      variant="outline" 
      size="sm" 
      @click="handleExport"
      :disabled="isLoading || stubsStore.stubs.length === 0"
    >
      导出所有
    </Button>
  </div>
</template>