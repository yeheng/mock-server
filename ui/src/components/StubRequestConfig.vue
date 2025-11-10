<script setup>
import { ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'

const props = defineProps({
  form: {
    type: Object,
    required: true,
  },
})

// 不向父层发事件，直接就地修改传入的 form

// 添加请求头
const addRequestHeader = () => {
  if (!props.form.request) props.form.request = { headers: {}, queryParameters: {}, bodyPatterns: [] }
  if (!props.form.request.headers) props.form.request.headers = {}
  props.form.request.headers[''] = ''
}

// 添加查询参数
const addQueryParameter = () => {
  if (!props.form.request) props.form.request = { headers: {}, queryParameters: {}, bodyPatterns: [] }
  if (!props.form.request.queryParameters) props.form.request.queryParameters = {}
  props.form.request.queryParameters[''] = ''
}

// 添加 body pattern
const addBodyPattern = () => {
  if (!props.form.request) props.form.request = { headers: {}, queryParameters: {}, bodyPatterns: [] }
  if (!props.form.request.bodyPatterns) props.form.request.bodyPatterns = []
  props.form.request.bodyPatterns.push('')
}

// 删除请求头
const removeRequestHeader = (key) => {
  if (!props.form.request?.headers) return
  delete props.form.request.headers[key]
}

// 删除查询参数
const removeQueryParameter = (key) => {
  if (!props.form.request?.queryParameters) return
  delete props.form.request.queryParameters[key]
}

// 删除 body pattern
const removeBodyPattern = (index) => {
  if (!props.form.request?.bodyPatterns) return
  props.form.request.bodyPatterns.splice(index, 1)
}

// 更新请求头值
const updateHeaderKey = (oldKey, newKey) => {
  if (oldKey === newKey) return

  if (!props.form.request) props.form.request = { headers: {}, queryParameters: {}, bodyPatterns: [] }
  if (!props.form.request.headers) props.form.request.headers = {}
  const value = props.form.request.headers[oldKey]
  delete props.form.request.headers[oldKey]
  props.form.request.headers[newKey] = value
}

// 更新请求头值
const updateHeaderValue = (key, value) => {
  if (!props.form.request) props.form.request = { headers: {}, queryParameters: {}, bodyPatterns: [] }
  if (!props.form.request.headers) props.form.request.headers = {}
  props.form.request.headers[key] = value
}

// 更新查询参数键
const updateQueryKey = (oldKey, newKey) => {
  if (oldKey === newKey) return

  if (!props.form.request) props.form.request = { headers: {}, queryParameters: {}, bodyPatterns: [] }
  if (!props.form.request.queryParameters) props.form.request.queryParameters = {}
  const value = props.form.request.queryParameters[oldKey]
  delete props.form.request.queryParameters[oldKey]
  props.form.request.queryParameters[newKey] = value
}

// 更新查询参数值
const updateQueryValue = (key, value) => {
  if (!props.form.request) props.form.request = { headers: {}, queryParameters: {}, bodyPatterns: [] }
  if (!props.form.request.queryParameters) props.form.request.queryParameters = {}
  props.form.request.queryParameters[key] = value
}

// 保持就地更新，避免函数调用形式的 v-model 表达式

// 无需更新到父层，直接改 props.form（输入即内存）
</script>

<template>
  <Card>
    <CardHeader>
      <CardTitle>请求匹配规则</CardTitle>
      <CardDescription> 定义哪些请求应该匹配此 stub </CardDescription>
    </CardHeader>
    <CardContent class="space-y-6">
      <!-- 请求头 -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <label class="text-sm font-medium">请求头</label>
          <Button size="sm" variant="outline" @click="addRequestHeader"> 添加头 </Button>
        </div>
        <div class="space-y-2">
          <div
            v-for="(value, key) in form.request?.headers"
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
            <Button size="sm" variant="outline" @click="removeRequestHeader(key)"> 删除 </Button>
          </div>
          <div
            v-if="!form.request?.headers || Object.keys(form.request.headers).length === 0"
            class="text-sm text-gray-500 text-center py-2"
          >
            暂无请求头，点击"添加头"按钮添加
          </div>
        </div>
      </div>

      <!-- 查询参数 -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <label class="text-sm font-medium">查询参数</label>
          <Button size="sm" variant="outline" @click="addQueryParameter"> 添加参数 </Button>
        </div>
        <div class="space-y-2">
          <div
            v-for="(value, key) in form.request?.queryParameters"
            :key="key"
            class="flex items-center space-x-2"
          >
            <Input
              :value="key"
              @input="updateQueryKey(key, $event.target.value)"
              placeholder="键"
              class="flex-1"
            />
            <Input
              :value="value"
              @input="updateQueryValue(key, $event.target.value)"
              placeholder="值"
              class="flex-1"
            />
            <Button size="sm" variant="outline" @click="removeQueryParameter(key)"> 删除 </Button>
          </div>
          <div
            v-if="
              !form.request?.queryParameters ||
              Object.keys(form.request.queryParameters).length === 0
            "
            class="text-sm text-gray-500 text-center py-2"
          >
            暂无查询参数，点击"添加参数"按钮添加
          </div>
        </div>
      </div>

      <!-- Body Patterns -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <label class="text-sm font-medium">Body 匹配模式</label>
          <Button size="sm" variant="outline" @click="addBodyPattern"> 添加模式 </Button>
        </div>
        <div class="space-y-2">
          <div
            v-for="(pattern, index) in form.request?.bodyPatterns"
            :key="index"
            class="flex items-center space-x-2"
          >
            <Input
              v-model="form.request.bodyPatterns[index]"
              placeholder="输入 JSONPath 或正则表达式"
              class="flex-1"
            />
            <Button size="sm" variant="outline" @click="removeBodyPattern(index)"> 删除 </Button>
          </div>
          <div
            v-if="!form.request?.bodyPatterns || form.request.bodyPatterns.length === 0"
            class="text-sm text-gray-500 text-center py-2"
          >
            暂无 body 匹配模式，点击"添加模式"按钮添加
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
