<script setup>
import { ref, computed } from 'vue'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'

const props = defineProps({
  form: {
    type: Object,
    required: true,
  },
  errors: {
    type: Object,
    default: () => ({}),
  },
})

const emit = defineEmits(['update:form'])

// HTTP 方法选项
const httpMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']

// 更新表单数据
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
      <CardTitle>基础信息</CardTitle>
      <CardDescription> 设置 stub 的基本属性 </CardDescription>
    </CardHeader>
    <CardContent class="space-y-4">
      <!-- 名称和描述 -->
      <div>
        <label class="block text-sm font-medium mb-2">名称 *</label>
        <Input
          :value="form.name"
          @input="updateForm('name', $event.target.value)"
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
          :value="form.description"
          @input="updateForm('description', $event.target.value)"
          placeholder="输入 stub 描述"
          class="w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          rows="3"
        />
      </div>

      <!-- HTTP 方法和 URL -->
      <div class="grid grid-cols-3 gap-4">
        <div>
          <label class="block text-sm font-medium mb-2">HTTP 方法 *</label>
          <select
            :value="form.method"
            @change="updateForm('method', $event.target.value)"
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
            :value="form.url"
            @input="updateForm('url', $event.target.value)"
            placeholder="/api/example"
            :class="{ 'border-red-500': errors.url }"
          />
          <p v-if="errors.url" class="text-red-500 text-sm mt-1">
            {{ errors.url }}
          </p>
        </div>
      </div>

      <!-- 优先级和启用状态 -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium mb-2">优先级</label>
          <Input
            :value="form.priority"
            @input="updateForm('priority', Number($event.target.value))"
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
            :checked="form.enabled"
            @update:checked="updateForm('enabled', $event)"
            id="enabled"
          />
          <label for="enabled" class="text-sm font-medium"> 启用此 stub </label>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
