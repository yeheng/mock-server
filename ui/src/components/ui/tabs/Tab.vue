<script setup>
import { inject, computed } from 'vue'

const props = defineProps({
  value: {
    type: String,
    required: true,
  },
  disabled: Boolean,
})

const context = inject('tabsContext')
const isActive = computed(() => context.activeTab === props.value)
</script>

<template>
  <button
    @click="context.setActiveTab(value)"
    :disabled="disabled"
    :class="[
      'py-2 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors',
      isActive
        ? 'border-primary text-primary'
        : 'border-transparent text-muted-foreground hover:text-foreground hover:border-muted-foreground',
      disabled && 'opacity-50 cursor-not-allowed',
    ]"
  >
    <slot></slot>
  </button>
</template>
