<script setup>
import { ref, computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'

const props = defineProps({
  form: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['update:form'])

// 添加响应头
const addResponseHeader = () => {
  const newHeaders = { ...props.form.response?.headers }
  newHeaders[''] = ''
  updateForm('response', {
    ...props.form.response,
    headers: newHeaders,
  })
}

// 删除响应头
const removeResponseHeader = (key) => {
  const newHeaders = { ...props.form.response?.headers }
  delete newHeaders[key]
  updateForm('response', {
    ...props.form.response,
    headers: newHeaders,
  })
}

// 更新响应头键
const updateHeaderKey = (oldKey, newKey) => {
  if (oldKey === newKey) return

  const headers = { ...props.form.response?.headers }
  const value = headers[oldKey]

  delete headers[oldKey]
  headers[newKey] = value

  updateForm('response', {
    ...props.form.response,
    headers,
  })
}

// 更新响应头值
const updateHeaderValue = (key, value) => {
  updateForm('response', {
    ...props.form.response,
    headers: {
      ...props.form.response?.headers,
      [key]: value,
    },
  })
}

// 更新响应体
const updateBody = (value) => {
  updateForm('response', {
    ...props.form.response,
    body: value,
  })
}

// 更新 bodyFileName
const updateBodyFileName = (value) => {
  updateForm('response', {
    ...props.form.response,
    bodyFileName: value,
  })
}

// 更新状态码
const updateStatus = (value) => {
  updateForm('response', {
    ...props.form.response,
    status: Number(value),
  })
}

// 更新表单
const updateForm = (field, value) => {
  emit('update:form', {
    ...props.form,
    [field]: value,
  })
}
</script>

<template>
  <Card>
    <CardHeader>
      <CardTitle>响应配置</CardTitle>
      <CardDescription> 配置此 stub 的响应行为 </CardDescription>
    </CardHeader>
    <CardContent class="space-y-4">
      <!-- 状态码 -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium mb-2">状态码 *</label>
          <Input
            :value="form.response?.status"
            @input="updateStatus($event.target.value)"
            type="number"
            min="100"
            max="599"
            placeholder="200"
            class="w-full px-3 py-2 border rounded-md"
          />
        </div>
        <div>
          <label class="block text-sm font-medium mb-2">常见状态码</label>
          <div class="flex flex-wrap gap-2">
            <Button
              v-for="code in [200, 201, 400, 401, 403, 404, 500]"
              :key="code"
              size="sm"
              variant="outline"
              @click="updateStatus(code)"
            >
              {{ code }}
            </Button>
          </div>
        </div>
      </div>

      <!-- 响应头 -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <label class="text-sm font-medium">响应头</label>
          <Button size="sm" variant="outline" @click="addResponseHeader"> 添加头 </Button>
        </div>
        <div class="space-y-2">
          <div
            v-for="(value, key) in form.response?.headers"
            :key="key"
            class="flex items-center space-x-2"
          >
            <Input
              :value="key"
              @input="updateHeaderKey(key, $event.target.value)"
              placeholder="键"
              class="flex-1"
            />
            <Input
              :value="value"
              @input="updateHeaderValue(key, $event.target.value)"
              placeholder="值"
              class="flex-1"
            />
            <Button size="sm" variant="outline" @click="removeResponseHeader(key)"> 删除 </Button>
          </div>
          <div
            v-if="!form.response?.headers || Object.keys(form.response.headers).length === 0"
            class="text-sm text-gray-500 text-center py-2"
          >
            暂无响应头，点击"添加头"按钮添加
          </div>
        </div>
      </div>

      <!-- 响应体模式选择 -->
      <div>
        <label class="block text-sm font-medium mb-2">响应体模式</label>
        <div class="flex space-x-4">
          <label class="flex items-center space-x-2 cursor-pointer">
            <input
              type="radio"
              :checked="!form.response?.bodyFileName"
              @change="updateBodyFileName(null)"
            />
            <span class="text-sm">直接响应体</span>
          </label>
          <label class="flex items-center space-x-2 cursor-pointer">
            <input type="radio" :checked="!!form.response?.bodyFileName" @change="updateBody('')" />
            <span class="text-sm">文件响应体</span>
          </label>
        </div>
      </div>

      <!-- 直接响应体 -->
      <div v-if="!form.response?.bodyFileName">
        <label class="block text-sm font-medium mb-2">响应体内容</label>
        <Textarea
          :value="form.response?.body"
          @input="updateBody($event.target.value)"
          placeholder="输入 JSON、XML 或纯文本响应体"
          rows="8"
          class="w-full"
        />
        <p class="text-sm text-gray-500 mt-1">支持 JSON、XML、HTML 或纯文本格式</p>
      </div>

      <!-- 文件响应体 -->
      <div v-else>
        <label class="block text-sm font-medium mb-2">响应体文件名</label>
        <Input
          :value="form.response?.bodyFileName"
          @input="updateBodyFileName($event.target.value)"
          placeholder="例如: response.json"
          class="w-full"
        />
        <p class="text-sm text-gray-500 mt-1">请确保文件存在于 WireMock 的 __files 目录中</p>
      </div>
    </CardContent>
  </Card>
</template>
