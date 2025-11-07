<script setup>
import { SelectRoot, SelectValue, SelectTrigger, SelectContent, SelectItem } from 'reka-ui'

const props = defineProps({
  modelValue: String,
  placeholder: String
})

const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <SelectRoot :value="modelValue" @update:modelValue="emit('update:modelValue', $event)">
    <SelectTrigger class="flex h-10 w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50">
      <SelectValue :placeholder="placeholder" />
    </SelectTrigger>

    <SelectContent class="relative z-50 max-h-96 min-w-[8rem] overflow-hidden rounded-md border bg-popover text-popover-foreground shadow-md">
      <SelectViewport class="p-1">
        <slot />
      </SelectViewport>
    </SelectContent>
  </SelectRoot>
</template>
