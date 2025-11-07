<script setup>
import { ref, computed, onMounted } from 'vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import { Pagination } from '@/components/ui/pagination'
import { AlertDialog } from '@/components/ui/alert-dialog'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'

const emit = defineEmits(['edit', 'create', 'view-details'])

const stubsStore = useStubsStore()

// 本地状态
const searchInput = ref('')
const isDeleting = ref(false)
const showDeleteConfirm = ref(false)
const stubToDelete = ref(null)

// 计算属性
const selectedStubsList = computed(() => 
  Array.from(stubsStore.selectedStubs)
)

const allVisibleSelected = computed(() => 
  stubsStore.stubs.length > 0 && 
  stubsStore.stubs.every(stub => stubsStore.selectedStubs.has(stub.id))
)

const hasSelection = computed(() => 
  stubsStore.selectedStubs.size > 0
)

// 初始化
onMounted(async () => {
  await stubsStore.fetchStubs()
})

// 搜索处理
const handleSearch = async () => {
  await stubsStore.searchStubs(searchInput.value)
}

// 分页处理
const handlePageChange = (page) => {
  stubsStore.fetchStubs(page, stubsStore.pageSize, stubsStore.searchKeyword)
}

const handleSizeChange = (size) => {
  stubsStore.fetchStubs(1, size, stubsStore.searchKeyword)
}

// 选择处理
const handleSelectStub = (id) => {
  if (stubsStore.selectedStubs.has(id)) {
    stubsStore.deselectStub(id)
  } else {
    stubsStore.selectStub(id)
  }
}

const handleSelectAll = () => {
  if (allVisibleSelected.value) {
    stubsStore.clearSelection()
  } else {
    stubsStore.selectAllVisible()
  }
}

// 操作处理
const handleToggle = async (id) => {
  try {
    await stubsStore.toggleStub(id)
  } catch (error) {
    console.error('Failed to toggle stub:', error)
  }
}

const handleDeleteClick = (stub) => {
  stubToDelete.value = stub
  showDeleteConfirm.value = true
}

const confirmDelete = async () => {
  if (!stubToDelete.value) return
  
  isDeleting.value = true
  try {
    await stubsStore.deleteStub(stubToDelete.value.id)
    stubToDelete.value = null
  } catch (error) {
    console.error('Failed to delete stub:', error)
  } finally {
    isDeleting.value = false
  }
}

const cancelDelete = () => {
  stubToDelete.value = null
}

const handleEdit = (stub) => {
  emit('edit', stub)
}

const handleViewDetails = (stub) => {
  emit('view-details', stub)
}

const handleCreate = () => {
  emit('create')
}

// 批量操作
const handleBatchDelete = async () => {
  if (selectedStubsList.value.length === 0) return
  
  if (confirm(`确定要删除选中的 ${selectedStubsList.value.length} 个stub吗？`)) {
    try {
      await stubsStore.batchDeleteStubs(selectedStubsList.value)
      stubsStore.clearSelection()
    } catch (error) {
      console.error('Failed to batch delete stubs:', error)
    }
  }
}

const handleBatchEnable = async () => {
  if (selectedStubsList.value.length === 0) return
  
  try {
    await stubsStore.batchToggleStubs(selectedStubsList.value, true)
    stubsStore.clearSelection()
  } catch (error) {
    console.error('Failed to batch enable stubs:', error)
  }
}

const handleBatchDisable = async () => {
  if (selectedStubsList.value.length === 0) return
  
  try {
    await stubsStore.batchToggleStubs(selectedStubsList.value, false)
    stubsStore.clearSelection()
  } catch (error) {
    console.error('Failed to batch disable stubs:', error)
  }
}

const handleReload = async () => {
  try {
    await stubsStore.reloadAllStubs()
  } catch (error) {
    console.error('Failed to reload stubs:', error)
  }
}

// 获取状态徽章样式
const getStatusBadge = (enabled) => {
  return enabled ? 'default' : 'destructive'
}

const getStatusText = (enabled) => {
  return enabled ? '已启用' : '已禁用'
}

// 格式化方法
const formatMethod = (method) => {
  const colors = {
    GET: 'bg-green-100 text-green-800',
    POST: 'bg-blue-100 text-blue-800',
    PUT: 'bg-yellow-100 text-yellow-800',
    DELETE: 'bg-red-100 text-red-800',
    PATCH: 'bg-purple-100 text-purple-800'
  }
  return colors[method] || 'bg-gray-100 text-gray-800'
}

const formatDate = (dateString) => {
  return new Date(dateString).toLocaleString('zh-CN')
}
</script>

<template>
  <Card>
    <CardHeader>
      <div class="flex items-center justify-between">
        <div>
          <CardTitle>Stub 管理</CardTitle>
          <CardDescription>
            管理和监控所有 WireMock stub 映射
          </CardDescription>
        </div>
        <div class="flex space-x-2">
          <Button @click="handleReload" variant="outline">
            重新加载
          </Button>
          <Button @click="handleCreate">
            创建 Stub
          </Button>
        </div>
      </div>
    </CardHeader>
    
    <CardContent>
      <!-- 搜索和筛选 -->
      <div class="flex items-center space-x-4 mb-6">
        <div class="flex-1">
          <Input
            v-model="searchInput"
            placeholder="搜索 stub 名称或 URL..."
            @keyup.enter="handleSearch"
          />
        </div>
        <Button @click="handleSearch" variant="outline">
          搜索
        </Button>
      </div>

      <!-- 批量操作 -->
      <div v-if="hasSelection" class="flex items-center space-x-2 mb-4 p-3 bg-muted rounded-lg">
        <span class="text-sm text-muted-foreground">
          已选中 {{ selectedStubsList.length }} 个项目
        </span>
        <Button size="sm" variant="outline" @click="handleBatchEnable">
          批量启用
        </Button>
        <Button size="sm" variant="outline" @click="handleBatchDisable">
          批量禁用
        </Button>
        <Button size="sm" variant="destructive" @click="handleBatchDelete">
          批量删除
        </Button>
        <Button size="sm" variant="ghost" @click="stubsStore.clearSelection()">
          取消选择
        </Button>
      </div>

      <!-- 表格 -->
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-12">
              <Checkbox 
                :model-value="allVisibleSelected"
                @update:model-value="handleSelectAll"
              />
            </TableHead>
            <TableHead>名称</TableHead>
            <TableHead>方法</TableHead>
            <TableHead>URL</TableHead>
            <TableHead>状态</TableHead>
            <TableHead>优先级</TableHead>
            <TableHead>创建时间</TableHead>
            <TableHead>操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <template v-if="stubsStore.stubs.length > 0">
            <TableRow v-for="stub in stubsStore.stubs" :key="stub.id">
              <TableCell>
                <Checkbox 
                  :model-value="stubsStore.isSelected(stub.id)"
                  @update:model-value="() => handleSelectStub(stub.id)"
                />
              </TableCell>
              <TableCell>
                <div>
                  <div class="font-medium">{{ stub.name }}</div>
                  <div v-if="stub.description" class="text-sm text-muted-foreground">
                    {{ stub.description }}
                  </div>
                </div>
              </TableCell>
              <TableCell>
                <span 
                  :class="[
                    'inline-flex items-center px-2 py-1 rounded text-xs font-medium',
                    formatMethod(stub.method)
                  ]"
                >
                  {{ stub.method }}
                </span>
              </TableCell>
              <TableCell class="font-mono text-sm">
                {{ stub.url }}
              </TableCell>
              <TableCell>
                <Badge :variant="getStatusBadge(stub.enabled)">
                  {{ getStatusText(stub.enabled) }}
                </Badge>
              </TableCell>
              <TableCell>
                <span class="text-sm">{{ stub.priority }}</span>
              </TableCell>
              <TableCell class="text-sm text-muted-foreground">
                {{ formatDate(stub.createdAt) }}
              </TableCell>
              <TableCell>
                <div class="flex items-center space-x-2">
                  <Button 
                    size="sm" 
                    variant="ghost"
                    @click="handleViewDetails(stub)"
                    title="查看详情"
                  >
                    查看
                  </Button>
                  <Button 
                    size="sm" 
                    variant="ghost"
                    @click="handleEdit(stub)"
                    title="编辑"
                  >
                    编辑
                  </Button>
                  <Button 
                    size="sm" 
                    variant="outline"
                    @click="handleToggle(stub.id)"
                    :disabled="stubsStore.loading"
                    :title="stub.enabled ? '禁用' : '启用'"
                  >
                    {{ stub.enabled ? '禁用' : '启用' }}
                  </Button>
                  <Button 
                    size="sm" 
                    variant="destructive"
                    @click="handleDeleteClick(stub)"
                    :disabled="isDeleting"
                    title="删除"
                  >
                    删除
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </template>
          <TableRow v-else>
            <TableCell colspan="8" class="text-center py-8">
              <div class="text-muted-foreground">
                {{ stubsStore.searchKeyword ? '未找到匹配的 stub' : '暂无 stub 数据' }}
              </div>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <!-- 分页 -->
      <Pagination
        v-if="stubsStore.totalElements > 0"
        :current-page="stubsStore.currentPage"
        :total-pages="stubsStore.totalPages"
        :total-elements="stubsStore.totalElements"
        :page-size="stubsStore.pageSize"
        :has-next-page="stubsStore.hasNextPage"
        :has-previous-page="stubsStore.hasPreviousPage"
        @page-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </CardContent>
  </Card>

  <!-- 删除确认模态框 -->
  <AlertDialog
    :open="showDeleteConfirm"
    :title="`确认删除 stub: ${stubToDelete?.name}`"
    description="您确定要删除此 stub 吗？此操作不可撤销。"
    @update:open="(open) => showDeleteConfirm = open"
    @confirm="confirmDelete"
  >
    <template #trigger>
      <span></span>
    </template>
    <template #cancel>
      <Button variant="outline" @click="cancelDelete">
        取消
      </Button>
    </template>
    <template #confirm>
      <Button 
        variant="destructive" 
        :disabled="isDeleting"
      >
        {{ isDeleting ? '删除中...' : '确认删除' }}
      </Button>
    </template>
  </AlertDialog>
</template>