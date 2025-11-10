<script setup>
import { ref, onErrorCaptured } from 'vue'

// 错误状态
const hasError = ref(false)
const errorMessage = ref('')
const errorInfo = ref(null)
// 运行环境标识：避免在模板中直接使用 import.meta
const isDev = import.meta.env.DEV

// 捕获子组件错误
onErrorCaptured((error, instance, info) => {
  console.error('Error caught by boundary:', error, info)

  hasError.value = true
  errorMessage.value = error.message || '发生了未知错误'
  errorInfo.value = {
    message: error.message,
    stack: error.stack,
    componentInfo: info,
    timestamp: new Date().toISOString(),
  }

  // 返回 false 阻止错误继续向上传播
  return false
})

// 重置错误状态
const resetError = () => {
  hasError.value = false
  errorMessage.value = ''
  errorInfo.value = null
}

// 刷新页面
const refreshPage = () => {
  window.location.reload()
}
</script>

<template>
  <div class="error-boundary">
    <div v-if="hasError" class="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div class="max-w-md w-full bg-white rounded-lg shadow-lg p-6">
        <!-- 错误图标 -->
        <div class="flex justify-center mb-4">
          <div class="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center">
            <svg class="w-8 h-8 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
          </div>
        </div>

        <!-- 错误标题 -->
        <h2 class="text-xl font-semibold text-center text-gray-900 mb-2">页面出现错误</h2>

        <!-- 错误消息 -->
        <p class="text-gray-600 text-center mb-4">
          {{ errorMessage }}
        </p>

        <!-- 操作按钮 -->
        <div class="space-y-2">
          <button
            @click="resetError"
            class="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 transition-colors"
          >
            重试
          </button>
          <button
            @click="refreshPage"
            class="w-full bg-gray-200 text-gray-800 py-2 px-4 rounded-md hover:bg-gray-300 transition-colors"
          >
            刷新页面
          </button>
        </div>

        <!-- 错误详情（开发环境） -->
        <div v-if="isDev && errorInfo" class="mt-4 p-3 bg-gray-50 rounded-md">
          <details class="text-sm">
            <summary class="cursor-pointer text-gray-700 font-medium">错误详情</summary>
            <pre class="mt-2 text-xs text-gray-600 overflow-auto">{{ errorInfo }}</pre>
          </details>
        </div>
      </div>
    </div>

    <!-- 正常内容 -->
    <slot v-else />
  </div>
</template>

<style scoped>
.error-boundary {
  width: 100%;
}
</style>
