<script setup>
import { TextareaRoot } from 'reka-ui'

const props = defineProps({
  modelValue: String,
  placeholder: String,
  disabled: Boolean,
  rows: {
    type: Number,
    default: 3
  }
})

const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <TextareaRoot
    :value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :rows="rows"
    @update:value="emit('update:modelValue', $event)"
    class="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
  />
</template>
