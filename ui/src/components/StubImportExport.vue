<script setup>
import { ref } from 'vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
import { useToast } from '@/components/ui/toast/use-toast'

const { toast } = useToast()
const stubsStore = useStubsStore()
const isLoading = ref(false)

// 导入 stub
const handleImport = async (event) => {
  const file = event.target.files[0]
  if (!file) return

  if (!file.name.endsWith('.json')) {
    toast({
      title: '错误',
      description: '只支持 JSON 文件',
      variant: 'destructive'
    })
    return
  }

  isLoading.value = true
  
  try {
    const text = await file.text()
    const data = JSON.parse(text)
    
    // 支持多种格式：单个 stub、stub 数组、WireMock 格式
    let stubsToImport = []
    
    if (Array.isArray(data)) {
      // 直接是 stub 数组
      stubsToImport = data
    } else if (data.mappings) {
      // WireMock 格式：{ "mappings": [...] }
      stubsToImport = data.mappings
    } else if (data.request || data.response) {
      // 单个 stub 对象
      stubsToImport = [data]
    } else {
      throw new Error('不支持的 JSON 格式')
    }
    
    // 验证并导入
    let importedCount = 0
    let skippedCount = 0
    
    for (const stubData of stubsToImport) {
      try {
        // 标准化 stub 数据
        const normalizedStub = normalizeStub(stubData)
        
        // 检查是否已存在（基于名称或请求特征）
        const exists = stubsStore.stubs.some(existing => 
          existing.name === normalizedStub.name ||
          (existing.method === normalizedStub.method && existing.url === normalizedStub.url)
        )
        
        if (!exists) {
          await stubsStore.createStub(normalizedStub)
          importedCount++
        } else {
          skippedCount++
        }
      } catch (error) {
        console.warn('Failed to import stub:', stubData, error)
        skippedCount++
      }
    }
    
    toast({
      title: '导入完成',
      description: `成功导入 ${importedCount} 个 stub，跳过 ${skippedCount} 个`
    })
    
  } catch (error) {
    console.error('Import failed:', error)
    toast({
      title: '导入失败',
      description: error.message,
      variant: 'destructive'
    })
  } finally {
    isLoading.value = false
    // 清空 file input
    event.target.value = ''
  }
}

// 导出 stub
const handleExport = async () => {
  if (stubsStore.stubs.length === 0) {
    toast({
      title: '提示',
      description: '没有 stub 可导出'
    })
    return
  }

  isLoading.value = true
  
  try {
    // 准备导出数据（WireMock 兼容格式）
    const exportData = {
      mappings: stubsStore.stubs.map(stub => ({
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
    
    toast({
      title: '导出成功',
      description: `已导出 ${stubsStore.stubs.length} 个 stub`
    })
    
  } catch (error) {
    console.error('Export failed:', error)
    toast({
      title: '导出失败',
      description: error.message,
      variant: 'destructive'
    })
  } finally {
    isLoading.value = false
  }
}

// 导出选中的 stub
const handleExportSelected = async (selectedStubs) => {
  if (selectedStubs.length === 0) {
    toast({
      title: '提示',
      description: '请先选择要导出的 stub'
    })
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
    
    toast({
      title: '导出成功',
      description: `已导出 ${selectedStubs.length} 个选中的 stub`
    })
    
  } catch (error) {
    console.error('Export selected failed:', error)
    toast({
      title: '导出失败',
      description: error.message,
      variant: 'destructive'
    })
  } finally {
    isLoading.value = false
  }
}

// 标准化 stub 数据格式
const normalizeStub = (stubData) => {
  // 确保有基本字段
  const stub = {
    name: stubData.name || `Imported-${Date.now()}`,
    method: stubData.request?.method || 'GET',
    url: stubData.request?.urlPattern || stubData.request?.url || '/',
    enabled: stubData.enabled !== false,
    priority: stubData.priority || 0,
    request: stubData.request || {},
    response: stubData.response || { status: 200, body: '' }
  }
  
  // 确保 response 有基本字段
  if (!stub.response.status) {
    stub.response.status = 200
  }
  if (!stub.response.headers) {
    stub.response.headers = { 'Content-Type': 'application/json' }
  }
  
  return stub
}

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