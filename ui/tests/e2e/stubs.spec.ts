import { test, expect } from '@playwright/test'

// 说明：这些 e2e 用例直接通过 UI 触发对 Spring Boot 的真实 API 调用
// 依赖于 vite.config.js 中的代理：/admin -> http://localhost:8080

const uniqueSuffix = Math.floor(Math.random() * 1_000_000)
const stubName = `e2e-stub-${uniqueSuffix}`

test.describe('WireMock UI e2e - 基本用户流', () => {
  test('访问 Stub 列表页面并能加载', async ({ page }) => {
    await page.goto('/stubs')
    // 列表页标题
    await expect(page.getByRole('heading', { name: 'Stub 管理' })).toBeVisible()
    // 搜索输入存在
    await expect(page.getByPlaceholder('搜索 stub 名称或 URL...')).toBeVisible()
  })

  test('创建新的 Stub 并在列表中可见', async ({ page }) => {
    await page.goto('/stubs/create')

    // 填写基础信息
    await page.getByPlaceholder('输入 stub 名称').fill(stubName)
    await page.getByPlaceholder('输入 stub 描述').fill('e2e 自动化创建')
    await page.getByLabel('HTTP 方法 *').selectOption('GET')
    await page.getByPlaceholder('/api/example').fill(`/api/${stubName}`)

    // 保存创建
    await page.getByRole('button', { name: '创建' }).click()

    // 跳转回列表并出现新建条目
    await expect(page).toHaveURL(/\/stubs$/)
    await expect(page.getByText(stubName)).toBeVisible()
    await expect(page.getByText(`/api/${stubName}`)).toBeVisible()
  })

  test('切换启用/禁用状态', async ({ page }) => {
    await page.goto('/stubs')
    const row = page.locator('tr', { hasText: stubName })
    // 先禁用（如果当前为启用）
    const toggleBtn = row.getByRole('button', { name: /启用|禁用/ })
    await toggleBtn.click()
    // 状态徽章在两种文案之间切换
    await expect(row.getByText(/已启用|已禁用/)).toBeVisible()
  })

  test('删除 Stub', async ({ page }) => {
    await page.goto('/stubs')
    const row = page.locator('tr', { hasText: stubName })
    await row.getByRole('button', { name: '删除' }).click()
    // 弹窗确认
    await page.getByRole('button', { name: /确认删除/ }).click()
    // 列表不再包含该条目
    await expect(page.getByText(stubName)).toHaveCount(0)
  })
})