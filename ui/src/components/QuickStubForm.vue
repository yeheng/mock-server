<script setup>
import { ref, reactive } from 'vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { useRouter } from 'vue-router'

const emit = defineEmits(['saved', 'close'])

const router = useRouter()
const stubsStore = useStubsStore()
const isLoading = ref(false)

// 快速表单数据 - 只包含最常用的字段
const form = reactive({
  name: '',
  method: 'GET',
  url: '',
  status: 200,
  body: '',
  enabled: true
})

// 常用状态码预设
const statusCodes = [
  { value: 200, label: '200 OK' },
  { value: 201, label: '201 Created' },
  { value: 204, label: '204 No Content' },
  { value: 400, label: '400 Bad Request' },
  { value: 401, label: '401 Unauthorized' },
  { value: 403, label: '403 Forbidden' },
  { value: 404, label: '404 Not Found' },
  { value: 500, label: '500 Internal Error' }
]

// 常用方法预设
const httpMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']

// 保存快速 stub
const handleSave = async () => {
  if (!form.name.trim() || !form.url.trim()) {
    return
  }

  isLoading.value = true
  try {
    const stubData = {
      name: form.name,
      method: form.method,
      url: form.url,
      enabled: form.enabled,
      priority: 0,
      responseDefinition: form.body
    }

    await stubsStore.createStub(stubData)
    emit('saved')
    
    // 重置表单但不关闭，方便连续创建
    form.name = ''
    form.url = ''
    form.body = ''
    
  } catch (error) {
    console.error('Failed to create quick stub:', error)
  } finally {
    isLoading.value = false
  }
}

// 关闭
const handleClose = () => {
  emit('close')
}

// 使用模板
const useTemplate = (template) => {
  Object.assign(form, template)
}

// 常用模板
const templates = [
  {
    name: '用户列表',
    method: 'GET',
    url: '/api/users',
    status: 200,
    body: JSON.stringify([
      { id: 1, name: '张三', email: 'zhangsan@example.com' },
      { id: 2, name: '李四', email: 'lisi@example.com' }
    ], null, 2)
  },
  {
    name: '创建用户',
    method: 'POST',
    url: '/api/users',
    status: 201,
    body: JSON.stringify({ id: 123, name: '新用户', email: 'new@example.com' }, null, 2)
  },
  {
    name: '获取用户详情',
    method: 'GET',
    url: '/api/users/{id}',
    status: 200,
    body: JSON.stringify({ id: 1, name: '用户详情', email: 'user@example.com' }, null, 2)
  },
  {
    name: '错误响应',
    method: 'GET',
    url: '/api/error',
    status: 500,
    body: JSON.stringify({ error: 'Internal Server Error', message: 'Something went wrong' }, null, 2)
  }
]
</script>

<template>
  <div class="space-y-4 p-4 bg-white rounded-lg border">
    <div class="flex items-center justify-between">
      <h3 class="text-lg font-semibold">快速创建 Stub</h3>
      <Button variant="ghost" size="sm" @click="handleClose">×</Button>
    </div>

    <!-- 模板选择 -->
    <div class="space-y-2">
      <Label>使用模板</Label>
      <div class="flex flex-wrap gap-2">
        <Button 
          v-for="(template, index) in templates" 
          :key="index"
          variant="outline" 
          size="sm"
          @click="useTemplate(template)"
          class="text-xs"
        >
          {{ template.name }}
        </Button>
      </div>
    </div>

    <!-- 基本信息 -->
    <div class="grid grid-cols-2 gap-4">
      <div class="space-y-2">
        <Label for="name">名称</Label>
        <Input 
          id="name" 
          v-model="form.name" 
          placeholder="Stub 名称"
        />
      </div>
      
      <div class="space-y-2">
        <Label for="method">HTTP 方法</Label>
        <Select v-model="form.method">
          <SelectTrigger>
            <SelectValue placeholder="选择方法" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem 
              v-for="method in httpMethods" 
              :key="method" 
              :value="method"
            >
              {{ method }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>

    <div class="space-y-2">
      <Label for="url">URL 路径</Label>
      <Input 
        id="url" 
        v-model="form.url" 
        placeholder="/api/endpoint"
      />
    </div>

    <div class="grid grid-cols-2 gap-4">
      <div class="space-y-2">
        <Label for="status">响应状态码</Label>
        <Select v-model="form.status">
          <SelectTrigger>
            <SelectValue placeholder="选择状态码" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem 
              v-for="status in statusCodes" 
              :key="status.value" 
              :value="status.value"
            >
              {{ status.label }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>
      
      <div class="space-y-2">
        <Label for="enabled">状态</Label>
        <div class="flex items-center space-x-2">
          <Switch id="enabled" v-model="form.enabled" />
          <Label for="enabled">{{ form.enabled ? '启用' : '禁用' }}</Label>
        </div>
      </div>
    </div>

    <div class="space-y-2">
      <Label for="body">响应体 (JSON)</Label>
      <textarea
        id="body"
        v-model="form.body"
        placeholder="输入 JSON 响应体"
        class="w-full h-32 px-3 py-2 border rounded-md resize-none font-mono text-sm"
      />
    </div>

    <!-- 操作按钮 -->
    <div class="flex justify-end space-x-2 pt-4">
      <Button variant="outline" @click="handleClose">取消</Button>
      <Button @click="handleSave" :disabled="isLoading">
        {{ isLoading ? '创建中...' : '创建 Stub' }}
      </Button>
    </div>
  </div>
</template>