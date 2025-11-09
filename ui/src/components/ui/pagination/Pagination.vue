<script setup>
import { Button } from '@/components/ui/button'

const props = defineProps({
  currentPage: Number,
  totalPages: Number,
  totalElements: Number,
  pageSize: Number,
  hasNextPage: Boolean,
  hasPreviousPage: Boolean,
})

const emit = defineEmits(['page-change', 'size-change'])

const startItem = computed(() => {
  if (props.totalElements === 0) return 0
  return (props.currentPage - 1) * props.pageSize + 1
})

const endItem = computed(() => {
  return Math.min(props.currentPage * props.pageSize, props.totalElements)
})

const goToPage = (page) => {
  if (page >= 1 && page <= props.totalPages) {
    emit('page-change', page)
  }
}

const goToPrevious = () => {
  goToPage(props.currentPage - 1)
}

const goToNext = () => {
  goToPage(props.currentPage + 1)
}
</script>

<template>
  <div class="flex items-center justify-between px-2 py-4">
    <div class="flex items-center space-x-2 text-sm text-muted-foreground">
      <span>Showing {{ startItem }} to {{ endItem }} of {{ totalElements }} results</span>
      <select
        class="ml-2 rounded border border-input bg-background px-2 py-1 text-xs"
        :value="pageSize"
        @change="$emit('size-change', parseInt($event.target.value))"
      >
        <option :value="10">10 per page</option>
        <option :value="20">20 per page</option>
        <option :value="50">50 per page</option>
        <option :value="100">100 per page</option>
      </select>
    </div>

    <div class="flex items-center space-x-2">
      <Button variant="outline" size="sm" :disabled="!hasPreviousPage" @click="goToPrevious">
        Previous
      </Button>

      <div class="flex items-center space-x-1">
        <template v-for="page in totalPages" :key="page">
          <Button
            v-if="Math.abs(page - currentPage) <= 2"
            :variant="page === currentPage ? 'default' : 'outline'"
            size="sm"
            @click="goToPage(page)"
          >
            {{ page }}
          </Button>
        </template>
      </div>

      <Button variant="outline" size="sm" :disabled="!hasNextPage" @click="goToNext"> Next </Button>
    </div>
  </div>
</template>
