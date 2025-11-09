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
    const mockResponse = {
      ok: true,
      json: vi.fn().mockResolvedValue({ id: '1', enabled: false }),
    }
    global.fetch = vi.fn().mockResolvedValue(mockResponse)

    store.stubs = [{ id: '1', enabled: true }]
    await store.toggleStub('1')

    expect(global.fetch).toHaveBeenCalledWith('/admin/stubs/1/toggle', expect.any(Object))
    expect(mockResponse.json).toHaveBeenCalled()
  })

  it('deletes a stub', async () => {
    const store = useStubsStore()
    // Mock the fetch function - need json() method for delete operation
    const mockResponse = {
      ok: true,
      json: vi.fn().mockResolvedValue({}),
    }
    global.fetch = vi.fn().mockResolvedValue(mockResponse)

    store.stubs = [{ id: '1' }]
    await store.deleteStub('1')

    expect(global.fetch).toHaveBeenCalledWith('/admin/stubs/1', { method: 'DELETE' })
  })
})
