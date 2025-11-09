<script setup>
import { ref, computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'

const props = defineProps({
  form: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['update:form'])

// 添加请求头
const addRequestHeader = () => {
  const newHeaders = { ...props.form.request?.headers }
  newHeaders[''] = ''
  updateForm('request', {
    ...props.form.request,
    headers: newHeaders,
  })
}

// 添加查询参数
const addQueryParameter = () => {
  const newParams = { ...props.form.request?.queryParameters }
  newParams[''] = ''
  updateForm('request', {
    ...props.form.request,
    queryParameters: newParams,
  })
}

// 添加 body pattern
const addBodyPattern = () => {
  const newPatterns = [...(props.form.request?.bodyPatterns || [])]
  newPatterns.push('')
  updateForm('request', {
    ...props.form.request,
    bodyPatterns: newPatterns,
  })
}

// 删除请求头
const removeRequestHeader = (key) => {
  const newHeaders = { ...props.form.request?.headers }
  delete newHeaders[key]
  updateForm('request', {
    ...props.form.request,
    headers: newHeaders,
  })
}

// 删除查询参数
const removeQueryParameter = (key) => {
  const newParams = { ...props.form.request?.queryParameters }
  delete newParams[key]
  updateForm('request', {
    ...props.form.request,
    queryParameters: newParams,
  })
}

// 删除 body pattern
const removeBodyPattern = (index) => {
  const newPatterns = [...(props.form.request?.bodyPatterns || [])]
  newPatterns.splice(index, 1)
  updateForm('request', {
    ...props.form.request,
    bodyPatterns: newPatterns,
  })
}

// 更新请求头值
const updateHeaderKey = (oldKey, newKey) => {
  if (oldKey === newKey) return

  const headers = { ...props.form.request?.headers }
  const value = headers[oldKey]

  delete headers[oldKey]
  headers[newKey] = value

  updateForm('request', {
    ...props.form.request,
    headers,
  })
}

// 更新请求头值
const updateHeaderValue = (key, value) => {
  updateForm('request', {
    ...props.form.request,
    headers: {
      ...props.form.request?.headers,
      [key]: value,
    },
  })
}

// 更新查询参数键
const updateQueryKey = (oldKey, newKey) => {
  if (oldKey === newKey) return

  const params = { ...props.form.request?.queryParameters }
  const value = params[oldKey]

  delete params[oldKey]
  params[newKey] = value

  updateForm('request', {
    ...props.form.request,
    queryParameters: params,
  })
}

// 更新查询参数值
const updateQueryValue = (key, value) => {
  updateForm('request', {
    ...props.form.request,
    queryParameters: {
      ...props.form.request?.queryParameters,
      [key]: value,
    },
  })
}

// 更新 body pattern
const updateBodyPattern = (index, value) => {
  const patterns = [...(props.form.request?.bodyPatterns || [])]
  patterns[index] = value
  updateForm('request', {
    ...props.form.request,
    bodyPatterns: patterns,
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
              :value="pattern"
              @input="updateBodyPattern(index, $event.target.value)"
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
