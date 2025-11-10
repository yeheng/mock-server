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

// 不向父层发事件，直接就地修改传入的 form

// 添加响应头
const addResponseHeader = () => {
  if (!props.form.response) props.form.response = { status: 200, headers: {}, body: '', bodyFileName: null }
  if (!props.form.response.headers) props.form.response.headers = {}
  props.form.response.headers[''] = ''
}

// 删除响应头
const removeResponseHeader = (key) => {
  if (!props.form.response?.headers) return
  delete props.form.response.headers[key]
}

// 更新响应头键
const updateHeaderKey = (oldKey, newKey) => {
  if (oldKey === newKey) return

  if (!props.form.response) props.form.response = { status: 200, headers: {}, body: '', bodyFileName: null }
  if (!props.form.response.headers) props.form.response.headers = {}
  const value = props.form.response.headers[oldKey]
  delete props.form.response.headers[oldKey]
  props.form.response.headers[newKey] = value
}

// 更新响应头值
const updateHeaderValue = (key, value) => {
  if (!props.form.response) props.form.response = { status: 200, headers: {}, body: '', bodyFileName: null }
  if (!props.form.response.headers) props.form.response.headers = {}
  props.form.response.headers[key] = value
}

// v-model 代理：状态码 / 响应体 / 文件名
const statusProxy = computed({
  get: () => props.form.response?.status ?? 200,
  set: (v) => {
    if (!props.form.response)
      props.form.response = { status: 200, headers: {}, body: '', bodyFileName: null }
    props.form.response.status = Number(v)
  },
})

const bodyProxy = computed({
  get: () => props.form.response?.body ?? '',
  set: (v) => {
    if (!props.form.response)
      props.form.response = { status: 200, headers: {}, body: '', bodyFileName: null }
    props.form.response.body = v
  },
})

const bodyFileNameProxy = computed({
  get: () => props.form.response?.bodyFileName ?? null,
  set: (v) => {
    if (!props.form.response)
      props.form.response = { status: 200, headers: {}, body: '', bodyFileName: null }
    props.form.response.bodyFileName = v
  },
})

// 无需向父层发事件，直接改 props.form（输入即内存）
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
            v-model="statusProxy"
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
              @click="statusProxy = code"
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
              @change="bodyFileNameProxy = null"
            />
            <span class="text-sm">直接响应体</span>
          </label>
          <label class="flex items-center space-x-2 cursor-pointer">
            <input type="radio" :checked="!!form.response?.bodyFileName" @change="bodyProxy = ''" />
            <span class="text-sm">文件响应体</span>
          </label>
        </div>
      </div>

      <!-- 直接响应体 -->
      <div v-if="!form.response?.bodyFileName">
        <label class="block text-sm font-medium mb-2">响应体内容</label>
        <Textarea
          v-model="bodyProxy"
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
          v-model="bodyFileNameProxy"
          placeholder="例如: response.json"
          class="w-full"
        />
        <p class="text-sm text-gray-500 mt-1">请确保文件存在于 WireMock 的 __files 目录中</p>
      </div>
    </CardContent>
  </Card>
</template>
