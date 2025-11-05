<script setup>
import { onMounted } from 'vue'
import StubList from '@/components/StubList.vue'
import { useStubsStore } from '@/stores/stubs'

const stubs = useStubsStore()

onMounted(() => {
  stubs.fetchAll()
})
</script>

<template>
  <div class="container mx-auto p-4">
    <header class="flex justify-between items-center py-4">
      <h1 class="text-2xl font-bold">WireMock UI</h1>
    </header>

    <main>
      <StubList />
    </main>
  </div>
</template>
