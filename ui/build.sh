#!/bin/bash

# WireMock UI 构建脚本

echo "🏗️  构建 WireMock UI..."

# 检查依赖
if [ ! -d "node_modules" ]; then
    echo "📦 安装依赖..."
    npm install
fi

# 运行测试（如果有）
echo "🧪 运行测试..."
npm run test:unit || echo "⚠️  测试失败，但继续构建"

# 构建生产版本
echo "🔧 构建生产版本..."
npm run build

# 检查构建结果
if [ $? -eq 0 ]; then
    echo "✅ 构建成功！"
    echo "📁 构建文件位于: ./dist/"
    echo "🚀 预览命令: npm run preview"
else
    echo "❌ 构建失败！"
    exit 1
fi