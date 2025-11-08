<script setup>
import { ref, computed, onMounted } from 'vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

const emit = defineEmits(['create-stub', 'view-stub', 'edit-stub'])

const stubsStore = useStubsStore()
const statistics = ref({
  totalStubs: 0,
  enabledStubs: 0,
  disabledStubs: 0,
  todayCreated: 0,
  totalRequests: 0,
  avgResponseTime: 0
})

const isLoading = ref(false)

// 统计卡片数据
const statCards = computed(() => [
  {
    title: '总 Stub 数量',
    value: statistics.value.totalStubs,
    description: '系统中所有 stub',
    icon: '📋',
    variant: 'default'
  },
  {
    title: '已启用',
    value: statistics.value.enabledStubs,
    description: '当前活跃的 stub',
    icon: '✅',
    variant: 'default'
  },
  {
    title: '已禁用',
    value: statistics.value.disabledStubs,
    description: '暂时禁用的 stub',
    icon: '⏸️',
    variant: 'secondary'
  },
  {
    title: '今日创建',
    value: statistics.value.todayCreated,
    description: '新创建的 stub',
    icon: '🆕',
    variant: 'default'
  }
])

// 快速操作
const quickActions = ref([
  {
    title: '创建新 Stub',
    description: '快速创建一个新的 stub 映射',
    icon: '➕',
    action: 'create',
    color: 'bg-blue-500 hover:bg-blue-600'
  },
  {
    title: '批量导入',
    description: '从 JSON 文件导入 stub',
    icon: '📤',
    action: 'import',
    color: 'bg-green-500 hover:bg-green-600'
  },
  {
    title: '导出配置',
    description: '导出所有 stub 为配置文件',
    icon: '💾',
    action: 'export',
    color: 'bg-purple-500 hover:bg-purple-600'
  },
  {
    title: '清理测试数据',
    description: '删除所有测试用的 stub',
    icon: '🧹',
    action: 'cleanup',
    color: 'bg-red-500 hover:bg-red-600'
  }
])

// 需求更新：移除最近活动模块

// 初始化
onMounted(async () => {
  await loadStatistics()
  await stubsStore.fetchStubs(1, 5) // 加载最近的5条记录用于概览
})

// 加载统计数据
const loadStatistics = async () => {
  isLoading.value = true
  try {
    const stats = await stubsStore.getStatistics()
    if (stats) {
      statistics.value = stats
    }
  } catch (error) {
    console.error('Failed to load statistics:', error)
  } finally {
    isLoading.value = false
  }
}

// 快速操作处理
const handleQuickAction = (action) => {
  switch (action) {
    case 'create':
      emit('create-stub')
      break
    case 'import':
      handleImport()
      break
    case 'export':
      handleExport()
      break
    case 'cleanup':
      handleCleanup()
      break
  }
}

// 导入处理
const handleImport = () => {
  // 实际项目中可以实现文件上传功能
  alert('导入功能开发中...')
}

// 导出处理
const handleExport = async () => {
  try {
    // 实际项目中可以实现导出功能
    alert('导出功能开发中...')
  } catch (error) {
    console.error('Export failed:', error)
  }
}

// 清理测试数据
const handleCleanup = async () => {
  if (confirm('确定要清理所有测试数据吗？此操作不可撤销。')) {
    try {
      // 实际项目中可以实现清理功能
      alert('清理功能开发中...')
    } catch (error) {
      console.error('Cleanup failed:', error)
    }
  }
}

// 获取活动类型图标
const getActivityIcon = (type) => {
  const icons = {
    create: '➕',
    update: '✏️',
    delete: '🗑️',
    toggle: '🔄'
  }
  return icons[type] || '📝'
}

// 获取活动类型颜色
const getActivityColor = (type) => {
  const colors = {
    create: 'text-green-600',
    update: 'text-blue-600',
    delete: 'text-red-600',
    toggle: 'text-purple-600'
  }
  return colors[type] || 'text-gray-600'
}
</script>

<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold">Stub 仪表板</h1>
        <p class="text-muted-foreground mt-1">
          WireMock stub 映射管理和监控中心
        </p>
      </div>
      <Button @click="$emit('create-stub')" size="lg">
        创建新 Stub
      </Button>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <Card v-for="(card, index) in statCards" :key="index" class="relative overflow-hidden">
        <CardHeader class="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle class="text-sm font-medium">
            {{ card.title }}
          </CardTitle>
          <span class="text-2xl">{{ card.icon }}</span>
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ card.value }}</div>
          <p class="text-xs text-muted-foreground">
            {{ card.description }}
          </p>
        </CardContent>
      </Card>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- 快速操作 -->
      <Card>
        <CardHeader>
          <CardTitle>快速操作</CardTitle>
          <CardDescription>
            常用的 stub 管理操作
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div class="grid grid-cols-2 gap-4">
            <div 
              v-for="action in quickActions" 
              :key="action.action"
              class="p-4 rounded-lg cursor-pointer transition-colors text-white"
              :class="action.color"
              @click="handleQuickAction(action.action)"
            >
              <div class="flex items-center space-x-2">
                <span class="text-xl">{{ action.icon }}</span>
                <div>
                  <div class="font-medium">{{ action.title }}</div>
                  <div class="text-xs opacity-90">{{ action.description }}</div>
                </div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      
    </div>

    <!-- 最近创建的 Stub -->
    <Card>
      <CardHeader>
        <div class="flex items-center justify-between">
          <div>
            <CardTitle>最近的 Stub</CardTitle>
            <CardDescription>
              最新创建的 stub 映射
            </CardDescription>
          </div>
          <Button variant="outline" @click="$emit('view-all')">
            查看全部
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        <div v-if="stubsStore.stubs.length > 0" class="space-y-3">
          <div 
            v-for="stub in stubsStore.stubs" 
            :key="stub.id"
            class="flex items-center justify-between p-3 border rounded-lg hover:bg-muted transition-colors"
          >
            <div class="flex items-center space-x-3">
              <Badge :variant="stub.enabled ? 'default' : 'secondary'">
                {{ stub.method }}
              </Badge>
              <div>
                <div class="font-medium">{{ stub.name }}</div>
                <div class="text-sm text-muted-foreground font-mono">
                  {{ stub.url }}
                </div>
              </div>
            </div>
            <div class="flex items-center space-x-2">
              <Button 
                size="sm" 
                variant="ghost"
                @click="$emit('view-stub', stub)"
              >
                查看
              </Button>
              <Button 
                size="sm" 
                variant="outline"
                @click="$emit('edit-stub', stub)"
              >
                编辑
              </Button>
            </div>
          </div>
        </div>
        <div v-else class="text-center py-8 text-muted-foreground">
          暂无 stub 数据
        </div>
      </CardContent>
    </Card>
  </div>
</template>