<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import { Tabs, Tab, TabPanel } from '@/components/ui/tabs'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useRouter } from 'vue-router'

const emit = defineEmits(['close', 'saved'])

const props = defineProps({
  stub: {
    type: Object,
    default: null
  }
})

const router = useRouter()

const stubsStore = useStubsStore()
const isLoading = ref(false)
const activeTab = ref('basic')

// 表单数据
const form = ref({
  id: null,
  name: '',
  description: '',
  method: 'GET',
  url: '',
  enabled: true,
  priority: 0,
  // Request matching
  request: {
    headers: {},
    queryParameters: {},
    bodyPatterns: []
  },
  // Response
  response: {
    status: 200,
    headers: {},
    body: '',
    bodyFileName: null
  }
})

// HTTP 方法选项
const httpMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']

// 表单验证
const errors = ref({})

// 是否为编辑模式
const isEdit = computed(() => !!props.stub)

// 监听 stub 变化
watch(() => props.stub, (newStub) => {
  if (newStub) {
    form.value = { ...newStub }
    if (newStub.request) {
      form.value.request = { ...newStub.request }
    }
    if (newStub.response) {
      form.value.response = { ...newStub.response }
    }
  } else {
    resetForm()
  }
}, { immediate: true })

// 初始化
onMounted(() => {
  if (props.stub) {
    form.value = { ...props.stub }
  }
})

// 重置表单（使用函数声明以避免在watch的immediate阶段引用未初始化）
function resetForm() {
  form.value = {
    id: null,
    name: '',
    description: '',
    method: 'GET',
    url: '',
    enabled: true,
    priority: 0,
    request: {
      headers: {},
      queryParameters: {},
      bodyPatterns: []
    },
    response: {
      status: 200,
      headers: {},
      body: '',
      bodyFileName: null
    }
  }
  errors.value = {}
  activeTab.value = 'basic'
}

// 验证表单
const validateForm = () => {
  errors.value = {}
  
  if (!form.value.name?.trim()) {
    errors.value.name = '名称不能为空'
  }
  
  if (!form.value.method) {
    errors.value.method = '请选择 HTTP 方法'
  }
  
  if (!form.value.url?.trim()) {
    errors.value.url = 'URL 不能为空'
  }
  
  if (form.value.priority < 0) {
    errors.value.priority = '优先级不能为负数'
  }
  
  return Object.keys(errors.value).length === 0
}

// 保存
const handleSave = async () => {
  if (!validateForm()) {
    return
  }
  
  isLoading.value = true
  try {
    if (isEdit.value) {
      await stubsStore.updateStub(form.value.id, form.value)
    } else {
      await stubsStore.createStub(form.value)
    }
    emit('saved')
    // 保存后返回列表页
    router.push('/stubs')
  } catch (error) {
    console.error('Failed to save stub:', error)
    // 可以在此处添加错误提示
  } finally {
    isLoading.value = false
  }
}

// 关闭
const handleClose = () => {
  resetForm()
  emit('close')
  router.back()
}

// 添加请求头
const addRequestHeader = () => {
  if (!form.value.request.headers) {
    form.value.request.headers = {}
  }
  form.value.request.headers[''] = ''
}

// 添加查询参数
const addQueryParameter = () => {
  if (!form.value.request.queryParameters) {
    form.value.request.queryParameters = {}
  }
  form.value.request.queryParameters[''] = ''
}

// 添加响应头
const addResponseHeader = () => {
  if (!form.value.response.headers) {
    form.value.response.headers = {}
  }
  form.value.response.headers[''] = ''
}

// 删除请求头
const removeRequestHeader = (key) => {
  if (form.value.request.headers && key in form.value.request.headers) {
    delete form.value.request.headers[key]
  }
}

// 删除查询参数
const removeQueryParameter = (key) => {
  if (form.value.request.queryParameters && key in form.value.request.queryParameters) {
    delete form.value.request.queryParameters[key]
  }
}

// 删除响应头
const removeResponseHeader = (key) => {
  if (form.value.response.headers && key in form.value.response.headers) {
    delete form.value.response.headers[key]
  }
}
 </script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold">{{ isEdit ? '编辑 Stub' : '创建新 Stub' }}</h1>
      <Button variant="outline" @click="handleClose">返回</Button>
    </div>

    <div class="space-y-6">
        <!-- 基本信息 -->
      <Card>
        <CardHeader>
          <CardTitle>基本信息</CardTitle>
          <CardDescription>
            设置 stub 的基本属性
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div>
            <label class="block text-sm font-medium mb-2">名称 *</label>
            <Input
              v-model="form.name"
              placeholder="输入 stub 名称"
              :class="{ 'border-red-500': errors.name }"
            />
            <p v-if="errors.name" class="text-red-500 text-sm mt-1">
              {{ errors.name }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium mb-2">描述</label>
            <textarea
              v-model="form.description"
              class="w-full px-3 py-2 border rounded-md"
              rows="3"
              placeholder="输入描述信息"
            />
          </div>

          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="block text-sm font-medium mb-2">HTTP 方法 *</label>
              <select
                v-model="form.method"
                class="w-full px-3 py-2 border rounded-md"
                :class="{ 'border-red-500': errors.method }"
              >
                <option value="">选择方法</option>
                <option v-for="method in httpMethods" :key="method" :value="method">
                  {{ method }}
                </option>
              </select>
              <p v-if="errors.method" class="text-red-500 text-sm mt-1">
                {{ errors.method }}
              </p>
            </div>

            <div class="col-span-2">
              <label class="block text-sm font-medium mb-2">URL *</label>
              <Input
                v-model="form.url"
                placeholder="/api/example"
                :class="{ 'border-red-500': errors.url }"
              />
              <p v-if="errors.url" class="text-red-500 text-sm mt-1">
                {{ errors.url }}
              </p>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium mb-2">优先级</label>
              <Input
                v-model.number="form.priority"
                type="number"
                min="0"
                placeholder="0"
                :class="{ 'border-red-500': errors.priority }"
              />
              <p v-if="errors.priority" class="text-red-500 text-sm mt-1">
                {{ errors.priority }}
              </p>
            </div>

            <div class="flex items-center space-x-2 mt-8">
              <Checkbox
                v-model="form.enabled"
                id="enabled"
              />
              <label for="enabled" class="text-sm font-medium">
                启用此 stub
              </label>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- 请求和响应配置 -->
      <Tabs v-model="activeTab">
        <Tab value="request" label="请求匹配" />
        <Tab value="response" label="响应配置" />
        <Tab value="advanced" label="高级配置" />

        <!-- 请求匹配 -->
        <TabPanel value="request">
          <Card>
            <CardHeader>
              <CardTitle>请求匹配规则</CardTitle>
              <CardDescription>
                定义哪些请求应该匹配此 stub
              </CardDescription>
            </CardHeader>
            <CardContent class="space-y-4">
              <!-- 请求头 -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <label class="text-sm font-medium">请求头</label>
                  <Button size="sm" variant="outline" @click="addRequestHeader">
                    添加头
                  </Button>
                </div>
                <div class="space-y-2">
                  <div 
                    v-for="(value, key) in form.request.headers" 
                    :key="key"
                    class="flex items-center space-x-2"
                  >
                    <Input
                      v-model="form.request.headers[key]"
                      placeholder="键"
                      class="flex-1"
                    />
                    <Input
                      v-model="form.request.headers[key]"
                      placeholder="值"
                      class="flex-1"
                    />
                    <Button 
                      size="sm" 
                      variant="outline" 
                      @click="removeRequestHeader(key)"
                    >
                      删除
                    </Button>
                  </div>
                  <div v-if="Object.keys(form.request.headers || {}).length === 0" 
                       class="text-sm text-muted-foreground">
                    暂无请求头配置
                  </div>
                </div>
              </div>

              <!-- 查询参数 -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <label class="text-sm font-medium">查询参数</label>
                  <Button size="sm" variant="outline" @click="addQueryParameter">
                    添加参数
                  </Button>
                </div>
                <div class="space-y-2">
                  <div 
                    v-for="(value, key) in form.request.queryParameters" 
                    :key="key"
                    class="flex items-center space-x-2"
                  >
                    <Input
                      v-model="form.request.queryParameters[key]"
                      placeholder="参数名"
                      class="flex-1"
                    />
                    <Input
                      v-model="form.request.queryParameters[key]"
                      placeholder="参数值"
                      class="flex-1"
                    />
                    <Button 
                      size="sm" 
                      variant="outline" 
                      @click="removeQueryParameter(key)"
                    >
                      删除
                    </Button>
                  </div>
                  <div v-if="Object.keys(form.request.queryParameters || {}).length === 0" 
                       class="text-sm text-muted-foreground">
                    暂无查询参数配置
                  </div>
                </div>
              </div>

              <!-- 请求体模式 -->
              <div>
                <label class="block text-sm font-medium mb-2">请求体匹配模式</label>
                <textarea
                  v-model="form.request.bodyPatterns"
                  class="w-full px-3 py-2 border rounded-md"
                  rows="4"
                  placeholder="输入 JSON 格式的请求体匹配模式"
                />
              </div>
            </CardContent>
          </Card>
        </TabPanel>

        <!-- 响应配置 -->
        <TabPanel value="response">
          <Card>
            <CardHeader>
              <CardTitle>响应配置</CardTitle>
              <CardDescription>
                定义匹配的请求应该返回什么响应
              </CardDescription>
            </CardHeader>
            <CardContent class="space-y-4">
              <div>
                <label class="block text-sm font-medium mb-2">状态码</label>
                <Input
                  v-model.number="form.response.status"
                  type="number"
                  min="100"
                  max="599"
                  placeholder="200"
                />
              </div>

              <!-- 响应头 -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <label class="text-sm font-medium">响应头</label>
                  <Button size="sm" variant="outline" @click="addResponseHeader">
                    添加头
                  </Button>
                </div>
                <div class="space-y-2">
                  <div 
                    v-for="(value, key) in form.response.headers" 
                    :key="key"
                    class="flex items-center space-x-2"
                  >
                    <Input
                      v-model="form.response.headers[key]"
                      placeholder="键"
                      class="flex-1"
                    />
                    <Input
                      v-model="form.response.headers[key]"
                      placeholder="值"
                      class="flex-1"
                    />
                    <Button 
                      size="sm" 
                      variant="outline" 
                      @click="removeResponseHeader(key)"
                    >
                      删除
                    </Button>
                  </div>
                  <div v-if="Object.keys(form.response.headers || {}).length === 0" 
                       class="text-sm text-muted-foreground">
                    暂无响应头配置
                  </div>
                </div>
              </div>

              <!-- 响应体 -->
              <div>
                <label class="block text-sm font-medium mb-2">响应体</label>
                <textarea
                  v-model="form.response.body"
                  class="w-full px-3 py-2 border rounded-md font-mono"
                  rows="8"
                  placeholder="输入响应体内容"
                />
              </div>

              <!-- 响应体文件 -->
              <div>
                <label class="block text-sm font-medium mb-2">响应体文件</label>
                <Input
                  v-model="form.response.bodyFileName"
                  placeholder="响应体文件名"
                />
              </div>
            </CardContent>
          </Card>
        </TabPanel>

        <!-- 高级配置 -->
        <TabPanel value="advanced">
          <Card>
            <CardHeader>
              <CardTitle>高级配置</CardTitle>
              <CardDescription>
                高级设置和配置选项
              </CardDescription>
            </CardHeader>
            <CardContent class="space-y-4">
              <div class="text-sm text-muted-foreground">
                高级配置功能开发中...
              </div>
            </CardContent>
          </Card>
        </TabPanel>
      </Tabs>
    </div>

    <!-- 操作按钮 -->
    <div class="flex justify-end space-x-2">
      <Button variant="outline" @click="handleClose" :disabled="isLoading">
        取消
      </Button>
      <Button @click="handleSave" :disabled="isLoading">
        {{ isLoading ? '保存中...' : (isEdit ? '更新' : '创建') }}
      </Button>
    </div>
  </div>
</template>