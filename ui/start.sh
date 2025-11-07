#!/bin/bash

# WireMock UI 启动脚本

echo "🚀 启动 WireMock UI..."

# 检查依赖
if [ ! -d "node_modules" ]; then
    echo "📦 安装依赖..."
    npm install
fi

# 启动开发服务器
echo "🔧 启动开发服务器..."
echo "🌐 访问地址: http://localhost:5173"
echo "📝 按 Ctrl+C 停止服务器"
echo ""

npm run dev