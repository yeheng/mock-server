<script setup>
import { computed } from 'vue'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
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

// 不向父层发事件，直接就地绑定传入的 form

// HTTP 方法选项
const httpMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']

// 直接双向绑定到传入的 form
const nameProxy = computed({ get: () => props.form.name || '', set: (v) => (props.form.name = v) })
const descriptionProxy = computed({
  get: () => props.form.description || '',
  set: (v) => (props.form.description = v),
})
const urlProxy = computed({ get: () => props.form.url || '', set: (v) => (props.form.url = v) })
const methodProxy = computed({ get: () => props.form.method, set: (v) => (props.form.method = v) })
const priorityProxy = computed({
  get: () => props.form.priority,
  set: (v) => (props.form.priority = Number(v)),
})
const enabledProxy = computed({ get: () => !!props.form.enabled, set: (v) => (props.form.enabled = v) })
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
          v-model="nameProxy"
          placeholder="输入 stub 名称"
          :class="{ 'border-red-500': errors.name }"
        />
        <p v-if="errors.name" class="text-red-500 text-sm mt-1">
          {{ errors.name }}
        </p>
      </div>

      <div>
        <label class="block text-sm font-medium mb-2">描述</label>
        <Textarea
          v-model="descriptionProxy"
          placeholder="输入 stub 描述"
          rows="3"
          class="w-full"
        />
      </div>

      <!-- HTTP 方法和 URL -->
      <div class="grid grid-cols-3 gap-4">
        <div>
          <label class="block text-sm font-medium mb-2">HTTP 方法 *</label>
          <select
            v-model="methodProxy"
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
            v-model="urlProxy"
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
            v-model="priorityProxy"
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
            v-model:checked="enabledProxy"
            id="enabled"
          />
          <label for="enabled" class="text-sm font-medium"> 启用此 stub </label>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
