# WireMock UI 管理界面

一个现代化的 WireMock stub 映射管理界面，基于 Vue 3 和 Tailwind CSS 构建。

## 功能特性

### 📊 仪表板 (Dashboard)
- **统计概览**: 总stub数量、启用/禁用状态、今日创建等关键指标
- **快速操作**: 创建stub、批量导入、导出配置、清理测试数据
- **最近活动**: 显示系统中的最新操作记录
- **最近Stub**: 展示最新创建的stub映射

### 📋 Stub 列表管理
- **分页浏览**: 支持分页和页面大小调整
- **搜索筛选**: 按名称和URL进行实时搜索
- **批量操作**: 批量启用/禁用/删除stub
- **状态管理**: 实时显示stub的启用状态
- **操作按钮**: 查看、编辑、启用/禁用、删除等操作

### ➕ 创建/编辑表单
- **基本信息**: 名称、描述、HTTP方法、URL、优先级、启用状态
- **请求匹配**: 
  - 请求头匹配
  - 查询参数匹配
  - 请求体模式匹配
- **响应配置**:
  - HTTP状态码
  - 响应头设置
  - 响应体内容或文件
- **表单验证**: 完整的前端验证和错误提示
- **标签页导航**: 分类组织不同的配置选项

### 🔍 Stub 详情查看
- **完整信息**: 显示stub的所有配置详情
- **标签页组织**: 概览、请求匹配、响应配置、元数据
- **测试功能**: 一键测试stub的响应
- **JSON配置**: 查看原始的JSON配置
- **复制功能**: 快速复制配置信息

### 🎨 现代化UI
- **响应式设计**: 适配桌面和移动设备
- **Tailwind CSS**: 现代化的样式系统
- **Vue 3**: 使用Composition API和响应式系统
- **组件化**: 高度可复用的UI组件
- **深色/浅色主题**: 支持主题切换

## 技术栈

- **前端框架**: Vue 3 (Composition API)
- **状态管理**: Pinia
- **构建工具**: Vite
- **样式框架**: Tailwind CSS
- **UI组件**: 自定义组件系统
- **路由**: Vue Router (可扩展)

## 项目结构

```
ui/src/
├── components/
│   ├── ui/                    # 基础UI组件
│   │   ├── button/
│   │   ├── input/
│   │   ├── modal/
│   │   ├── tabs/
│   │   └── ...
│   ├── StubDashboard.vue      # 仪表板组件
│   ├── StubList.vue          # Stub列表组件
│   ├── StubForm.vue          # 创建/编辑表单
│   └── StubDetails.vue       # 详情查看组件
├── stores/
│   └── stubs.js              # Stub状态管理
├── lib/
│   └── utils.js              # 工具函数
└── App.vue                   # 主应用组件
```

## 核心功能流程

1. **用户登录** → 进入仪表板查看概览
2. **浏览Stub** → 查看stub列表，使用搜索和筛选功能
3. **创建Stub** → 填写表单配置请求匹配和响应
4. **管理Stub** → 编辑、启用/禁用、删除操作
5. **查看详情** → 深入了解stub配置和测试功能
6. **批量操作** → 提高管理效率

## API接口

后端提供以下RESTful API：

- `GET /admin/stubs/page` - 分页获取stub列表
- `POST /admin/stubs` - 创建新stub
- `PUT /admin/stubs/{id}` - 更新stub
- `DELETE /admin/stubs/{id}` - 删除stub
- `POST /admin/stubs/{id}/toggle` - 切换stub状态
- `POST /admin/stubs/reload` - 重新加载所有stub
- `GET /admin/stubs/statistics` - 获取统计信息

## 特性亮点

### 用户体验
- 流畅的单页应用体验
- 实时的搜索和筛选
- 直观的状态指示
- 便捷的批量操作

### 开发体验
- TypeScript支持（可扩展）
- 组件化设计，易于维护
- 完整的错误处理
- 响应式设计，适配多设备

### 扩展性
- 模块化架构
- 可配置的主题系统
- 插件化的功能模块
- 多语言支持框架

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 预览构建结果
npm run preview
```

## 开发计划

- [ ] 添加用户认证和权限管理
- [ ] 实现实时日志查看
- [ ] 集成WireMock录制功能
- [ ] 添加性能监控面板
- [ ] 支持stub模板和复用
- [ ] 实现导入/导出功能
- [ ] 添加API文档生成

## 贡献指南

欢迎提交Issue和Pull Request来帮助改进这个项目。

## 许可证

MIT License