<script setup>
import { ref } from 'vue'
import { useCounterStore } from './stores/counter'

const counter = useCounterStore()
const message = ref('WireMock UI Manager')
</script>

<template>
  <div class="min-h-screen bg-gray-50 flex items-center justify-center">
    <div class="max-w-md mx-auto bg-white rounded-lg shadow-lg p-8">
      <h1 class="text-3xl font-bold text-gray-900 mb-6 text-center">
        {{ message }}
      </h1>
      
      <div class="text-center space-y-4">
        <p class="text-gray-600">
          计数器: <span class="font-semibold text-blue-600">{{ counter.count }}</span>
        </p>
        
        <div class="space-x-2">
          <button 
            @click="counter.increment"
            class="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
          >
            增加
          </button>
          <button 
            @click="counter.decrement"
            class="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 transition-colors"
          >
            减少
          </button>
        </div>
        
        <p class="text-sm text-gray-500 mt-4">
          使用 Vite + Vue + Tailwind + Pinia + shadcn-vue
        </p>
      </div>
    </div>
  </div>
</template>
