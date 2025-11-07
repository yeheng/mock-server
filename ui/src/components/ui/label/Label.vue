<script setup>
import { Label } from 'reka-ui'

const props = defineProps({
  for: String
})
</script>

<template>
  <Label :for="for" class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
    <slot />
  </Label>
</template>
