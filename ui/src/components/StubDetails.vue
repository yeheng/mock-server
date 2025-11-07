<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Tabs, Tab, TabPanel } from '@/components/ui/tabs'

const emit = defineEmits(['close', 'edit'])

const props = defineProps({
  stub: {
    type: Object,
    required: true
  },
  show: {
    type: Boolean,
    default: false
  }
})

const stubsStore = useStubsStore()
const isLoading = ref(false)
const activeTab = ref('overview')
const stubDetails = ref(null)

// 监听 stub 变化
watch(() => props.stub, async (newStub) => {
  if (newStub && props.show) {
    await loadStubDetails(newStub.id)
  }
}, { immediate: true })

// 监听 show 变化
watch(() => props.show, async (show) => {
  if (show && props.stub) {
    await loadStubDetails(props.stub.id)
  }
})

// 加载stub详情
const loadStubDetails = async (id) => {
  isLoading.value = true
  try {
    const stub = await stubsStore.getStubById(id)
    if (stub) {
      stubDetails.value = stub
    }
  } catch (error) {
    console.error('Failed to load stub details:', error)
  } finally {
    isLoading.value = false
  }
}

// 关闭
const handleClose = () => {
  stubDetails.value = null
  emit('close')
}

// 编辑
const handleEdit = () => {
  emit('edit', props.stub)
  handleClose()
}

// 切换启用状态
const handleToggle = async () => {
  if (!stubDetails.value) return
  
  try {
    await stubsStore.toggleStub(stubDetails.value.id)
    stubDetails.value.enabled = !stubDetails.value.enabled
  } catch (error) {
    console.error('Failed to toggle stub:', error)
  }
}

// 格式化时间
const formatDate = (dateString) => {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleString('zh-CN')
}

// 格式化JSON
const formatJSON = (obj) => {
  if (!obj) return '{}'
  try {
    return JSON.stringify(obj, null, 2)
  } catch (error) {
    return String(obj)
  }
}

// 复制到剪贴板
const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    // 实际项目中可以添加成功提示
  } catch (error) {
    console.error('Failed to copy to clipboard:', error)
  }
}

// 获取HTTP方法样式
const getMethodStyle = (method) => {
  const styles = {
    GET: 'bg-green-100 text-green-800',
    POST: 'bg-blue-100 text-blue-800',
    PUT: 'bg-yellow-100 text-yellow-800',
    DELETE: 'bg-red-100 text-red-800',
    PATCH: 'bg-purple-100 text-purple-800',
    HEAD: 'bg-gray-100 text-gray-800',
    OPTIONS: 'bg-indigo-100 text-indigo-800'
  }
  return styles[method] || 'bg-gray-100 text-gray-800'
}

// Mock 请求测试结果
const testResult = ref(null)
const isTesting = ref(false)

// 测试stub
const testStub = async () => {
  if (!stubDetails.value) return
  
  isTesting.value = true
  try {
    // 模拟测试请求
    await new Promise(resolve => setTimeout(resolve, 1000))
    
    testResult.value = {
      success: true,
      status: stubDetails.value.response?.status || 200,
      headers: stubDetails.value.response?.headers || {},
      body: stubDetails.value.response?.body || '',
      responseTime: Math.floor(Math.random() * 500) + 50
    }
  } catch (error) {
    testResult.value = {
      success: false,
      error: 'Test failed'
    }
  } finally {
    isTesting.value = false
  }
}
</script>

<template>
  <Dialog
    :open="show"
    :title="stubDetails?.name || stub?.name || 'Stub 详情'"
    @update:open="(open) => { if (!open) handleClose() }"
  >
    <div class="max-h-[80vh] overflow-y-auto pr-2">
      <div v-if="isLoading" class="flex justify-center py-8">
        <div class="text-muted-foreground">加载中...</div>
      </div>
      
      <div v-else-if="stubDetails" class="space-y-6">
        <!-- 头部信息 -->
        <Card>
          <CardHeader>
            <div class="flex items-start justify-between">
              <div class="space-y-2">
                <div class="flex items-center space-x-3">
                  <Badge :variant="stubDetails.enabled ? 'default' : 'secondary'">
                    {{ stubDetails.method }}
                  </Badge>
                  <span 
                    :class="[
                      'inline-flex items-center px-2 py-1 rounded text-xs font-medium',
                      getMethodStyle(stubDetails.method)
                    ]"
                  >
                    {{ stubDetails.method }}
                  </span>
                  <Badge :variant="stubDetails.enabled ? 'default' : 'destructive'">
                    {{ stubDetails.enabled ? '已启用' : '已禁用' }}
                  </Badge>
                </div>
                <CardTitle>{{ stubDetails.name }}</CardTitle>
                <CardDescription v-if="stubDetails.description">
                  {{ stubDetails.description }}
                </CardDescription>
              </div>
              <div class="flex items-center space-x-2">
                <Button size="sm" variant="outline" @click="testStub" :disabled="isTesting">
                  {{ isTesting ? '测试中...' : '测试' }}
                </Button>
                <Button size="sm" variant="outline" @click="handleEdit">
                  编辑
                </Button>
                <Button 
                  size="sm" 
                  variant="outline" 
                  @click="handleToggle"
                  :disabled="isLoading"
                >
                  {{ stubDetails.enabled ? '禁用' : '启用' }}
                </Button>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div class="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
              <div>
                <div class="text-muted-foreground">URL</div>
                <div class="font-mono">{{ stubDetails.url }}</div>
              </div>
              <div>
                <div class="text-muted-foreground">优先级</div>
                <div>{{ stubDetails.priority }}</div>
              </div>
              <div>
                <div class="text-muted-foreground">创建时间</div>
                <div>{{ formatDate(stubDetails.createdAt) }}</div>
              </div>
              <div>
                <div class="text-muted-foreground">更新时间</div>
                <div>{{ formatDate(stubDetails.updatedAt) }}</div>
              </div>
            </div>
          </CardContent>
        </Card>

        <!-- 测试结果 -->
        <Card v-if="testResult">
          <CardHeader>
            <CardTitle>测试结果</CardTitle>
          </CardHeader>
          <CardContent>
            <div v-if="testResult.success" class="space-y-4">
              <div class="flex items-center space-x-2 text-green-600">
                <span>✅</span>
                <span>测试成功</span>
              </div>
              <div class="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <div class="text-muted-foreground">状态码</div>
                  <div>{{ testResult.status }}</div>
                </div>
                <div>
                  <div class="text-muted-foreground">响应时间</div>
                  <div>{{ testResult.responseTime }}ms</div>
                </div>
              </div>
            </div>
            <div v-else class="text-red-600">
              ❌ 测试失败: {{ testResult.error }}
            </div>
          </CardContent>
        </Card>

        <!-- 详细信息标签页 -->
        <Tabs v-model="activeTab">
          <Tab value="overview" label="概览" />
          <Tab value="request" label="请求匹配" />
          <Tab value="response" label="响应配置" />
          <Tab value="metadata" label="元数据" />

          <!-- 概览 -->
          <TabPanel value="overview">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <Card>
                <CardHeader>
                  <CardTitle>基本信息</CardTitle>
                </CardHeader>
                <CardContent class="space-y-3 text-sm">
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">名称</span>
                    <span>{{ stubDetails.name }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">方法</span>
                    <Badge :variant="getMethodStyle(stubDetails.method)">
                      {{ stubDetails.method }}
                    </Badge>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">URL</span>
                    <code class="text-xs">{{ stubDetails.url }}</code>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">状态</span>
                    <Badge :variant="stubDetails.enabled ? 'default' : 'secondary'">
                      {{ stubDetails.enabled ? '已启用' : '已禁用' }}
                    </Badge>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">优先级</span>
                    <span>{{ stubDetails.priority }}</span>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>创建信息</CardTitle>
                </CardHeader>
                <CardContent class="space-y-3 text-sm">
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">创建时间</span>
                    <span>{{ formatDate(stubDetails.createdAt) }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">更新时间</span>
                    <span>{{ formatDate(stubDetails.updatedAt) }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">ID</span>
                    <code class="text-xs">{{ stubDetails.id }}</code>
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabPanel>

          <!-- 请求匹配 -->
          <TabPanel value="request">
            <div class="space-y-6">
              <Card>
                <CardHeader>
                  <CardTitle>请求头匹配</CardTitle>
                </CardHeader>
                <CardContent>
                  <div v-if="stubDetails.request?.headers && Object.keys(stubDetails.request.headers).length > 0" 
                       class="space-y-2">
                    <div 
                      v-for="(value, key) in stubDetails.request.headers" 
                      :key="key"
                      class="flex items-center justify-between p-2 bg-muted rounded"
                    >
                      <div class="flex-1">
                        <code class="text-sm">{{ key }}</code>
                        <code class="text-sm ml-2 text-muted-foreground">{{ value }}</code>
                      </div>
                      <Button 
                        size="sm" 
                        variant="ghost"
                        @click="copyToClipboard(`${key}: ${value}`)"
                      >
                        复制
                      </Button>
                    </div>
                  </div>
                  <div v-else class="text-sm text-muted-foreground">
                    暂无请求头匹配规则
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>查询参数匹配</CardTitle>
                </CardHeader>
                <CardContent>
                  <div v-if="stubDetails.request?.queryParameters && Object.keys(stubDetails.request.queryParameters).length > 0" 
                       class="space-y-2">
                    <div 
                      v-for="(value, key) in stubDetails.request.queryParameters" 
                      :key="key"
                      class="flex items-center justify-between p-2 bg-muted rounded"
                    >
                      <div class="flex-1">
                        <code class="text-sm">{{ key }}</code>
                        <code class="text-sm ml-2 text-muted-foreground">{{ value }}</code>
                      </div>
                      <Button 
                        size="sm" 
                        variant="ghost"
                        @click="copyToClipboard(`${key}=${value}`)"
                      >
                        复制
                      </Button>
                    </div>
                  </div>
                  <div v-else class="text-sm text-muted-foreground">
                    暂无查询参数匹配规则
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>请求体匹配</CardTitle>
                </CardHeader>
                <CardContent>
                  <div v-if="stubDetails.request?.bodyPatterns?.length" class="space-y-2">
                    <pre class="text-xs bg-muted p-3 rounded overflow-x-auto">
{{ formatJSON(stubDetails.request.bodyPatterns) }}
                    </pre>
                  </div>
                  <div v-else class="text-sm text-muted-foreground">
                    暂无请求体匹配规则
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabPanel>

          <!-- 响应配置 -->
          <TabPanel value="response">
            <div class="space-y-6">
              <Card>
                <CardHeader>
                  <CardTitle>响应状态</CardTitle>
                </CardHeader>
                <CardContent>
                  <div class="text-2xl font-bold">
                    {{ stubDetails.response?.status || 200 }}
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>响应头</CardTitle>
                </CardHeader>
                <CardContent>
                  <div v-if="stubDetails.response?.headers && Object.keys(stubDetails.response.headers).length > 0" 
                       class="space-y-2">
                    <div 
                      v-for="(value, key) in stubDetails.response.headers" 
                      :key="key"
                      class="flex items-center justify-between p-2 bg-muted rounded"
                    >
                      <div class="flex-1">
                        <code class="text-sm">{{ key }}</code>
                        <code class="text-sm ml-2 text-muted-foreground">{{ value }}</code>
                      </div>
                      <Button 
                        size="sm" 
                        variant="ghost"
                        @click="copyToClipboard(`${key}: ${value}`)"
                      >
                        复制
                      </Button>
                    </div>
                  </div>
                  <div v-else class="text-sm text-muted-foreground">
                    暂无响应头设置
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>响应体</CardTitle>
                </CardHeader>
                <CardContent>
                  <div v-if="stubDetails.response?.bodyFileName" class="mb-4">
                    <div class="text-sm text-muted-foreground">使用文件</div>
                    <code class="text-sm">{{ stubDetails.response.bodyFileName }}</code>
                  </div>
                  <div v-if="stubDetails.response?.body">
                    <pre class="text-xs bg-muted p-3 rounded overflow-x-auto max-h-64">
{{ stubDetails.response.body }}
                    </pre>
                  </div>
                  <div v-if="!stubDetails.response?.body && !stubDetails.response?.bodyFileName" 
                       class="text-sm text-muted-foreground">
                    暂无响应体设置
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabPanel>

          <!-- 元数据 -->
          <TabPanel value="metadata">
            <Card>
              <CardHeader>
                <CardTitle>完整的 Stub 配置</CardTitle>
                <CardDescription>
                  原始的 JSON 配置信息
                </CardDescription>
              </CardHeader>
              <CardContent>
                <pre class="text-xs bg-muted p-3 rounded overflow-x-auto max-h-96">
{{ formatJSON(stubDetails) }}
                </pre>
              </CardContent>
            </Card>
          </TabPanel>
        </Tabs>
      </div>

      <div v-else class="text-center py-8 text-muted-foreground">
        无法加载 stub 详情
      </div>
    </div>
  </Dialog>
</template>
