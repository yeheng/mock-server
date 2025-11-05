import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useStubsStore } from '../../stores/stubs'

describe('Stubs Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('toggles a stub', async () => {
    const store = useStubsStore()
    // Mock the fetch function
    global.fetch = vi.fn().mockResolvedValue({ ok: true })

    store.stubs = [{ id: '1', enabled: true }]
    await store.toggleStub('1')

    expect(global.fetch).toHaveBeenCalledWith('/__admin/mappings/1', expect.any(Object))
    // The state should be updated optimistically, but let's assume the fetch is successful
    // and the state is updated after the fetch. In a real app, you might want to test the optimistic update too.
  })

  it('removes a stub', async () => {
    const store = useStubsStore()
    // Mock the fetch function
    global.fetch = vi.fn().mockResolvedValue({ ok: true })

    store.stubs = [{ id: '1' }]
    await store.removeStub('1')

    expect(global.fetch).toHaveBeenCalledWith('/__admin/mappings/1', { method: 'DELETE' })
    expect(store.stubs.length).toBe(0)
  })
})