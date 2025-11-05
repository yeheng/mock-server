import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useStubsStore = defineStore('stubs', () => {
  const stubs = ref([])

  const fetchStubs = async () => {
    try {
      const response = await fetch('/__admin/mappings')
      const data = await response.json()
      stubs.value = data.mappings
    } catch (error) {
      console.error('Error fetching stubs:', error)
    }
  }

  const toggleStub = async (id) => {
    const stub = stubs.value.find(s => s.id === id)
    if (!stub) return

    const newEnabledState = !stub.enabled

    try {
      await fetch(`/__admin/mappings/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...stub, enabled: newEnabledState }),
      })
      stub.enabled = newEnabledState
    } catch (error) {
      console.error(`Error toggling stub ${id}:`, error)
    }
  }

  const removeStub = async (id) => {
    try {
      await fetch(`/__admin/mappings/${id}`, { method: 'DELETE' })
      stubs.value = stubs.value.filter(s => s.id !== id)
    } catch (error) {
      console.error(`Error removing stub ${id}:`, error)
    }
  }

  return { stubs, fetchStubs, toggleStub, removeStub }
})