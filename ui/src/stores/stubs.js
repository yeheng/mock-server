import { defineStore } from 'pinia'

// 简单的 WireMock Stub 数据模型（JSDoc用于开发提示）
/**
 * @typedef {Object} StubRequest
 * @property {string} method
 * @property {string} url
 * @property {{[key:string]: string}} [headers]
 * @property {string} [body]
 */

/**
 * @typedef {Object} StubResponse
 * @property {number} status
 * @property {string} [body]
 * @property {{[key:string]: string}} [headers]
 */

/**
 * @typedef {Object} Stub
 * @property {string} id
 * @property {StubRequest} request
 * @property {StubResponse} response
 * @property {string[]} [tags]
 * @property {boolean} enabled
 */

export const useStubsStore = defineStore('stubs', {
  state: () => ({
    /** @type {Stub[]} */
    items: [],
  }),

  getters: {
    enabledStubs: state => state.items.filter(i => i.enabled),
    disabledStubs: state => state.items.filter(i => !i.enabled),
  },

  actions: {
    async fetchAll() {
      try {
        const response = await fetch('/__admin/mappings');
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        this.items = data.mappings;
      } catch (error) {
        console.error("Could not fetch stubs:", error);
        // In a real app, you'd want to show a notification to the user
        this.items = [];
      }
    },

    toggle(id) {
      const stub = this.items.find(i => i.id === id)
      if (stub) {
        stub.enabled = !stub.enabled
      }
    },

    remove(id) {
      this.items = this.items.filter(i => i.id !== id)
    },
  },
})