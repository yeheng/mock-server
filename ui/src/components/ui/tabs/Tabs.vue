<script setup>
import { provide, ref, computed } from 'vue'

const props = defineProps({
  modelValue: String,
  defaultValue: String,
})

const emit = defineEmits(['update:modelValue'])

const activeTab = ref(props.modelValue || props.defaultValue)

const tabs = ref([])

const setActiveTab = (value) => {
  activeTab.value = value
  emit('update:modelValue', value)
}

provide('tabsContext', {
  tabs,
  activeTab,
  setActiveTab,
})
</script>

<template>
  <div class="w-full">
    <div class="border-b border-border">
      <nav class="-mb-px flex space-x-8">
        <slot name="tabs"></slot>
      </nav>
    </div>
    <div class="mt-4">
      <slot></slot>
    </div>
  </div>
</template>
