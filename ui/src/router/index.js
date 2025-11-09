import { createRouter, createWebHistory } from 'vue-router'
import StubDashboard from '@/components/StubDashboard.vue'
import StubList from '@/components/StubList.vue'
import StubForm from '@/components/StubForm.vue'

const routes = [
  { path: '/', name: 'dashboard', component: StubDashboard, meta: { title: '仪表板' } },
  { path: '/stubs', name: 'stubs', component: StubList, meta: { title: 'Stub 列表' } },
  {
    path: '/stubs/create',
    name: 'stubs-create',
    component: StubForm,
    meta: { title: '创建 Stub' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
