<script setup>
import { SelectItem, SelectItemText, SelectItemIndicator } from 'reka-ui'

const props = defineProps({
  value: String,
  disabled: Boolean,
})
</script>

<template>
  <SelectItem
    :value="value"
    :disabled="disabled"
    class="relative flex w-full cursor-default select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
  >
    <SelectItemIndicator class="absolute left-2 inline-flex items-center justify-center">
      <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
      </svg>
    </SelectItemIndicator>
    <SelectItemText>
      <slot />
    </SelectItemText>
  </SelectItem>
</template>
