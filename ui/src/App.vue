<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter, RouterView, RouterLink } from 'vue-router'
import StubDetails from '@/components/StubDetails.vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

const stubsStore = useStubsStore()
const editingStub = ref(null)
const viewingStub = ref(null)
const showDetails = ref(false)
const route = useRoute()
const router = useRouter()

// 导航菜单
const navigationItems = [
  { id: 'dashboard', label: '仪表板', icon: '📊', description: '概览和统计', to: '/' },
  { id: 'list', label: 'Stub 列表', icon: '📋', description: '管理所有 stub', to: '/stubs' },
  { id: 'create', label: '创建 Stub', icon: '➕', description: '新建 stub 映射', to: '/stubs/create' }
]

// 当前页面标题
const pageTitle = computed(() => route.meta?.title || 'WireMock UI')

// 初始化
onMounted(() => {
  // 可以在这里进行一些初始化操作
})

// 导航到页面
const navigateTo = (to) => {
  router.push(to)
}

// 创建新 stub
const handleCreateStub = () => {
  editingStub.value = null
  router.push('/stubs/create')
}

// 编辑 stub
const handleEditStub = (stub) => {
  editingStub.value = stub
  router.push('/stubs/create')
}

// 查看 stub 详情
const handleViewStub = (stub) => {
  viewingStub.value = stub
  showDetails.value = true
}

// 表单保存成功
const handleFormSaved = () => {
  editingStub.value = null
}

// 表单取消
const handleFormClose = () => {
  editingStub.value = null
}

// 详情关闭
const handleDetailsClose = () => {
  showDetails.value = false
  viewingStub.value = null
}

// 详情编辑
const handleDetailsEdit = (stub) => {
  showDetails.value = false
  viewingStub.value = null
  handleEditStub(stub)
}

// 获取 WireMock 连接状态
const wiremockStatus = ref('connected') // 'connected', 'disconnected', 'error'
</script>

<template>
  <div class="min-h-screen bg-background">
    <!-- 顶部导航栏 -->
    <header class="border-b bg-white shadow-sm">
      <div class="container mx-auto px-4">
        <div class="flex items-center justify-between h-16">
          <!-- 左侧标题和状态 -->
          <div class="flex items-center space-x-4">
            <h1 class="text-xl font-bold">WireMock UI Manager</h1>
            <Badge 
              :variant="wiremockStatus === 'connected' ? 'default' : 'destructive'"
              class="text-xs"
            >
              {{ wiremockStatus === 'connected' ? '🟢 已连接' : '🔴 未连接' }}
            </Badge>
          </div>

          <!-- 右侧操作 -->
          <div class="flex items-center space-x-2">
            <Button 
              @click="handleCreateStub" 
              size="sm"
              class="bg-blue-600 hover:bg-blue-700"
            >
              <span class="mr-1">➕</span>
              创建 Stub
            </Button>
          </div>
        </div>
      </div>
    </header>

    <!-- 主内容区域 -->
    <div class="flex">
      <!-- 侧边导航 -->
      <aside class="w-64 border-r bg-white h-[calc(100vh-4rem)]">
        <nav class="p-4">
          <div class="space-y-2">
            <RouterLink
              v-for="item in navigationItems"
              :key="item.id"
              :to="item.to"
              class="flex items-center space-x-3 px-3 py-2 rounded-lg transition-colors hover:bg-gray-50"
            >
              <span class="text-lg">{{ item.icon }}</span>
              <div>
                <div class="font-medium">{{ item.label }}</div>
                <div class="text-xs text-muted-foreground">
                  {{ item.description }}
                </div>
              </div>
            </RouterLink>
          </div>

          <!-- 快速统计 -->
          <div class="mt-8 pt-6 border-t">
            <h3 class="text-sm font-medium text-muted-foreground mb-3">快速统计</h3>
            <div class="space-y-2 text-sm">
              <div class="flex justify-between">
                <span>总 Stub</span>
                <span class="font-medium">{{ stubsStore.stubs.length }}</span>
              </div>
              <div class="flex justify-between">
                <span>已启用</span>
                <span class="font-medium text-green-600">
                  {{ stubsStore.stubs.filter(s => s.enabled).length }}
                </span>
              </div>
              <div class="flex justify-between">
                <span>已禁用</span>
                <span class="font-medium text-gray-500">
                  {{ stubsStore.stubs.filter(s => !s.enabled).length }}
                </span>
              </div>
            </div>
          </div>
        </nav>
      </aside>

      <!-- 主内容 -->
      <main class="flex-1 p-6 overflow-auto">
        <!-- 页面标题 -->
        <div class="mb-6">
          <h2 class="text-2xl font-bold">{{ pageTitle }}</h2>
          <p class="text-muted-foreground mt-1">
            欢迎使用 WireMock stub 管理界面
          </p>
          <!-- 面包屑导航 -->
          <nav class="text-sm text-muted-foreground mt-2">
            <ol class="flex items-center space-x-2">
              <li>
                <RouterLink to="/" class="hover:underline">首页</RouterLink>
              </li>
              <li v-for="m in route.matched" :key="m.path" class="flex items-center space-x-2">
                <span>›</span>
                <span>{{ m.meta?.title }}</span>
              </li>
            </ol>
          </nav>
        </div>

        <!-- 路由页面内容 -->
        <RouterView 
          @create-stub="handleCreateStub"
          @view-stub="handleViewStub"
          @edit-stub="handleEditStub"
        />
      </main>
    </div>

    <!-- Stub 详情查看 -->
    <StubDetails
      :stub="viewingStub"
      :show="showDetails"
      @close="handleDetailsClose"
      @edit="handleDetailsEdit"
    />
  </div>
</template>

<style>
/* 基础样式 */
.container {
  max-width: 1200px;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a1a1a1;
}
</style>
