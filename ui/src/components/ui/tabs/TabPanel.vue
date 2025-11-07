<script setup>
import { inject, computed } from 'vue'

const props = defineProps({
  value: {
    type: String,
    required: true
  }
})

const context = inject('tabsContext')
const isActive = computed(() => context.activeTab === props.value)
</script>

<template>
  <div v-if="isActive" class="focus:outline-none">
    <slot></slot>
  </div>
</template>