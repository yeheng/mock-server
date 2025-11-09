<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  disabled: Boolean,
  id: String,
})

const emit = defineEmits(['update:modelValue'])

const isChecked = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
</script>

<template>
  <div class="flex items-center space-x-2">
    <input
      v-model="isChecked"
      :id="id"
      type="checkbox"
      :disabled="disabled"
      class="h-4 w-4 rounded border border-primary text-primary focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
    />
    <label
      v-if="$slots.default"
      :for="id"
      class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
    >
      <slot></slot>
    </label>
  </div>
</template>
