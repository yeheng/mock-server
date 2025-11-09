<script setup>
import { ref, computed, watch } from 'vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
import { Tabs, Tab, TabPanel } from '@/components/ui/tabs'
import StubBasicInfo from './StubBasicInfo.vue'
import StubRequestConfig from './StubRequestConfig.vue'
import StubResponseConfig from './StubResponseConfig.vue'
import { useRouter } from 'vue-router'

const emit = defineEmits(['close', 'saved'])

const props = defineProps({
  stub: {
    type: Object,
    default: null,
  },
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
  request: {
    headers: {},
    queryParameters: {},
    bodyPatterns: [],
  },
  response: {
    status: 200,
    headers: {},
    body: '',
    bodyFileName: null,
  },
})

// 表单验证
const errors = ref({})

// 是否为编辑模式
const isEdit = computed(() => !!props.stub)

// 监听 stub 变化
watch(
  () => props.stub,
  (newStub) => {
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
  },
  { immediate: true }
)

// 重置表单
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
      bodyPatterns: [],
    },
    response: {
      status: 200,
      headers: {},
      body: '',
      bodyFileName: null,
    },
  }
  errors.value = {}
  activeTab.value = 'basic'
}

// 更新表单数据
const updateForm = (newForm) => {
  form.value = newForm
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
    router.push('/stubs')
  } catch (error) {
    console.error('Failed to save stub:', error)
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
</script>

<template>
  <div class="space-y-6">
    <!-- 页面标题和返回按钮 -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold">{{ isEdit ? '编辑 Stub' : '创建新 Stub' }}</h1>
      <Button variant="outline" @click="handleClose">返回</Button>
    </div>

    <div class="space-y-6">
      <!-- 基本信息 -->
      <StubBasicInfo :form="form" :errors="errors" @update:form="updateForm" />

      <!-- 请求和响应配置 -->
      <Tabs v-model="activeTab">
        <Tab value="request" label="请求匹配" />
        <Tab value="response" label="响应配置" />
        <Tab value="advanced" label="高级配置" />

        <!-- 请求匹配 -->
        <TabPanel value="request">
          <StubRequestConfig :form="form" @update:form="updateForm" />
        </TabPanel>

        <!-- 响应配置 -->
        <TabPanel value="response">
          <StubResponseConfig :form="form" @update:form="updateForm" />
        </TabPanel>

        <!-- 高级配置 -->
        <TabPanel value="advanced">
          <div class="text-center py-8 text-gray-500">高级配置功能开发中...</div>
        </TabPanel>
      </Tabs>
    </div>

    <!-- 操作按钮 -->
    <div class="flex justify-end space-x-2">
      <Button variant="outline" @click="handleClose" :disabled="isLoading"> 取消 </Button>
      <Button @click="handleSave" :disabled="isLoading">
        {{ isLoading ? '保存中...' : isEdit ? '更新' : '创建' }}
      </Button>
    </div>
  </div>
</template>
