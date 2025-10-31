// WireMock UI Manager 主要 JavaScript 文件

// 全局变量
let currentSection = 'dashboard';
let stubs = [];
let logs = [];

// API 基础配置
const API_BASE = '/api/v1';

// 初始化应用
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

function initializeApp() {
    loadDashboard();
    loadStubs();
    updateWireMockStatus();
    
    // 定期刷新数据
    setInterval(updateWireMockStatus, 30000); // 30秒更新一次状态
    setInterval(loadRecentActivities, 60000); // 1分钟更新一次活动
}

// 界面切换函数
function showDashboard() {
    showSection('dashboard-section');
    loadDashboard();
    updateActiveNav('dashboard');
}

function showStubs() {
    showSection('stubs-section');
    loadStubs();
    updateActiveNav('stubs');
}

function showLogs() {
    showSection('logs-section');
    loadLogs();
    updateActiveNav('logs');
}

function showSection(sectionId) {
    // 隐藏所有内容区域
    const sections = document.querySelectorAll('.content-section');
    sections.forEach(section => {
        section.classList.add('d-none');
    });
    
    // 显示目标区域
    const targetSection = document.getElementById(sectionId);
    if (targetSection) {
        targetSection.classList.remove('d-none');
    }
    
    currentSection = sectionId.replace('-section', '');
}

function updateActiveNav(section) {
    // 更新导航活动状态
    const navLinks = document.querySelectorAll('.navbar-nav .nav-link');
    navLinks.forEach(link => {
        link.classList.remove('active');
    });
    
    const activeLink = document.querySelector(`[onclick="show${section.charAt(0).toUpperCase() + section.slice(1)}()"]`);
    if (activeLink) {
        activeLink.classList.add('active');
    }
}

// 仪表盘功能
function loadDashboard() {
    loadStubStatistics();
}

function loadStubStatistics() {
    fetch(`${API_BASE}/stubs/statistics`)
        .then(response => response.json())
        .then(data => {
            document.getElementById('total-stubs').textContent = data.totalStubs;
            document.getElementById('enabled-stubs').textContent = data.enabledStubs;
            document.getElementById('disabled-stubs').textContent = data.disabledStubs;
        })
        .catch(error => {
            console.error('加载Stub统计失败:', error);
            showToast('加载Stub统计失败', 'error');
        });
}

function loadRecentActivities() {
    // 加载最近的stub创建和修改活动
    loadStubs();
}

// WireMock服务器管理
function updateWireMockStatus() {
    fetch(`${API_BASE}/wiremock/status`)
        .then(response => response.json())
        .then(data => {
            const statusElement = document.getElementById('wiremock-status');
            const portElement = document.getElementById('wiremock-port');
            const urlElement = document.getElementById('wiremock-url');
            const adminUrlElement = document.getElementById('wiremock-admin-url');
            const serverStatusElement = document.getElementById('server-status');
            
            if (data.running) {
                statusElement.textContent = '运行中';
                statusElement.className = 'badge bg-success';
                serverStatusElement.textContent = 'WireMock正常';
                serverStatusElement.className = 'badge bg-success';
            } else {
                statusElement.textContent = '已停止';
                statusElement.className = 'badge bg-danger';
                serverStatusElement.textContent = 'WireMock异常';
                serverStatusElement.className = 'badge bg-danger';
            }
            
            portElement.textContent = data.port || '-';
            urlElement.textContent = data.serverUrl || '-';
            urlElement.innerHTML = data.serverUrl ? `<a href="${data.serverUrl}" target="_blank">${data.serverUrl}</a>` : '-';
            adminUrlElement.innerHTML = data.adminUrl ? `<a href="${data.adminUrl}" target="_blank">${data.adminUrl}</a>` : '-';
        })
        .catch(error => {
            console.error('更新WireMock状态失败:', error);
            const statusElement = document.getElementById('wiremock-status');
            const serverStatusElement = document.getElementById('server-status');
            
            if (statusElement) {
                statusElement.textContent = '连接异常';
                statusElement.className = 'badge bg-danger';
            }
            if (serverStatusElement) {
                serverStatusElement.textContent = '连接异常';
                serverStatusElement.className = 'badge bg-danger';
            }
        });
}

// Stub管理功能
function loadStubs() {
    showLoading(true);
    
    fetch(`${API_BASE}/stubs`)
        .then(response => response.json())
        .then(data => {
            stubs = data;
            renderStubsTable(stubs);
            showLoading(false);
        })
        .catch(error => {
            console.error('加载Stubs失败:', error);
            showToast('加载Stubs失败', 'error');
            showLoading(false);
        });
}

function renderStubsTable(stubsData) {
    const tbody = document.getElementById('stubs-table-body');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    stubsData.forEach(stub => {
        const row = document.createElement('tr');
        const statusClass = stub.enabled ? 'status-enabled' : 'status-disabled';
        const statusText = stub.enabled ? '启用' : '禁用';
        
        row.innerHTML = `
            <td>
                <strong>${stub.name}</strong>
                ${stub.description ? `<br><small class="text-muted">${stub.description}</small>` : ''}
            </td>
            <td><span class="badge bg-info">${stub.method}</span></td>
            <td><code>${stub.url}</code></td>
            <td><span class="badge ${statusClass}">${statusText}</span></td>
            <td>${new Date(stub.createdAt).toLocaleString()}</td>
            <td>
                <div class="btn-group" role="group">
                    <button class="btn btn-sm btn-outline-primary" onclick="editStub(${stub.id})" 
                            title="编辑">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-warning" onclick="toggleStubEnabled(${stub.id})" 
                            title="${stub.enabled ? '禁用' : '启用'}">
                        <i class="bi bi-${stub.enabled ? 'pause' : 'play'}"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger" onclick="deleteStub(${stub.id})" 
                            title="删除">
                        <i class="bi bi-trash"></i>
                    </button>
                </div>
            </td>
        `;
        
        tbody.appendChild(row);
    });
}

function searchStubs() {
    const keyword = document.getElementById('stub-search-input').value.trim();
    
    if (!keyword) {
        loadStubs();
        return;
    }
    
    fetch(`${API_BASE}/stubs/search?keyword=${encodeURIComponent(keyword)}`)
        .then(response => response.json())
        .then(data => {
            stubs = data;
            renderStubsTable(stubs);
        })
        .catch(error => {
            console.error('搜索Stubs失败:', error);
            showToast('搜索Stubs失败', 'error');
        });
}

function clearSearch() {
    document.getElementById('stub-search-input').value = '';
    loadStubs();
}

function createStub() {
    const form = document.getElementById('createStubForm');
    const formData = new FormData(form);
    
    const stubData = {
        name: formData.get('name'),
        method: formData.get('method'),
        url: formData.get('url'),
        urlMatchType: formData.get('urlMatchType') || 'EQUALS',
        requestBodyPattern: formData.get('requestBodyPattern') || null,
        responseDefinition: formData.get('responseDefinition'),
        priority: parseInt(formData.get('priority')) || 0,
        enabled: formData.get('enabled') === 'on'
    };
    
    if (!stubData.name || !stubData.method || !stubData.url || !stubData.responseDefinition) {
        showToast('请填写所有必需字段', 'error');
        return;
    }
    
    showLoading(true);
    
    fetch(`${API_BASE}/stubs`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(stubData)
    })
    .then(response => {
        if (response.ok) {
            showToast('Stub创建成功', 'success');
            bootstrap.Modal.getInstance(document.getElementById('createStubModal')).hide();
            form.reset();
            loadStubs();
        } else {
            throw new Error('创建失败');
        }
    })
    .catch(error => {
        console.error('创建Stub失败:', error);
        showToast('创建Stub失败', 'error');
    })
    .finally(() => {
        showLoading(false);
    });
}

function toggleStubEnabled(stubId) {
    fetch(`${API_BASE}/stubs/${stubId}/toggle`, {
        method: 'POST'
    })
    .then(response => {
        if (response.ok) {
            return response.json();
        } else {
            throw new Error('操作失败');
        }
    })
    .then(updatedStub => {
        showToast(`Stub已${updatedStub.enabled ? '启用' : '禁用'}`, 'success');
        loadStubs();
        loadStubStatistics();
    })
    .catch(error => {
        console.error('切换Stub状态失败:', error);
        showToast('操作失败', 'error');
    });
}

function deleteStub(stubId) {
    if (!confirm('确定要删除这个Stub吗？')) {
        return;
    }
    
    fetch(`${API_BASE}/stubs/${stubId}`, {
        method: 'DELETE'
    })
    .then(response => {
        if (response.ok) {
            showToast('Stub已删除', 'success');
            loadStubs();
            loadStubStatistics();
        } else {
            throw new Error('删除失败');
        }
    })
    .catch(error => {
        console.error('删除Stub失败:', error);
        showToast('删除失败', 'error');
    });
}

function editStub(stubId) {
    const stub = stubs.find(s => s.id === stubId);
    if (!stub) {
        showToast('Stub不存在', 'error');
        return;
    }
    
    showToast('编辑功能待实现', 'info');
}

// WireMock服务器操作
function reloadAllStubs() {
    showLoading(true);
    
    fetch(`${API_BASE}/stubs/reload`, {
        method: 'POST'
    })
    .then(response => {
        if (response.ok) {
            showToast('所有Stubs已重新加载', 'success');
            loadStubs();
        } else {
            throw new Error('重载失败');
        }
    })
    .catch(error => {
        console.error('重新加载Stubs失败:', error);
        showToast('重新加载失败', 'error');
    })
    .finally(() => {
        showLoading(false);
    });
}

function resetWireMock() {
    if (!confirm('确定要重置WireMock服务器吗？这将清空所有请求日志。')) {
        return;
    }
    
    fetch(`${API_BASE}/wiremock/reset`, {
        method: 'POST'
    })
    .then(response => {
        if (response.ok) {
            showToast('WireMock服务器已重置', 'success');
            loadLogs();
        } else {
            throw new Error('重置失败');
        }
    })
    .catch(error => {
        console.error('重置WireMock失败:', error);
        showToast('重置失败', 'error');
    });
}

function clearRequestLogs() {
    fetch(`${API_BASE}/wiremock/clear-logs`, {
        method: 'POST'
    })
    .then(response => {
        if (response.ok) {
            showToast('请求日志已清空', 'success');
            loadLogs();
        } else {
            throw new Error('清空失败');
        }
    })
    .catch(error => {
        console.error('清空请求日志失败:', error);
        showToast('清空失败', 'error');
    });
}

// 日志功能
function loadLogs() {
    fetch(`${API_BASE}/wiremock/logs`)
        .then(response => response.json())
        .then(data => {
            logs = data;
            renderLogsTable(logs);
        })
        .catch(error => {
            console.error('加载日志失败:', error);
            showToast('加载日志失败', 'error');
        });
}

function renderLogsTable(logsData) {
    const tbody = document.getElementById('logs-table-body');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    logsData.forEach(log => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${log.timestamp || '-'}</td>
            <td><span class="badge bg-info">${log.method || log.request?.method || '-'}</span></td>
            <td><code>${log.url || log.request?.url || '-'}</code></td>
            <td><span class="badge bg-secondary">${log.response?.status || '-'}</span></td>
            <td>
                <button class="btn btn-sm btn-outline-info" onclick="showLogDetail('${log.id}')">
                    <i class="bi bi-eye"></i> 查看
                </button>
            </td>
        `;
        
        tbody.appendChild(row);
    });
}

function showLogDetail(logId) {
    const log = logs.find(l => l.id === logId);
    if (!log) {
        showToast('日志不存在', 'error');
        return;
    }
    
    const detailContent = document.getElementById('log-detail-content');
    detailContent.innerHTML = `
        <div class="row">
            <div class="col-md-6">
                <h6>请求信息</h6>
                <p><strong>方法:</strong> ${log.method || log.request?.method || '-'}</p>
                <p><strong>URL:</strong> <code>${log.url || log.request?.url || '-'}</code></p>
                <p><strong>时间:</strong> ${log.timestamp || '-'}</p>
                ${log.request?.headers ? `<p><strong>请求头:</strong> <pre>${JSON.stringify(log.request.headers, null, 2)}</pre></p>` : ''}
                ${log.request?.body ? `<p><strong>请求体:</strong> <pre>${log.request.body}</pre></p>` : ''}
            </div>
            <div class="col-md-6">
                <h6>响应信息</h6>
                <p><strong>状态码:</strong> ${log.response?.status || '-'}</p>
                ${log.response?.headers ? `<p><strong>响应头:</strong> <pre>${JSON.stringify(log.response.headers, null, 2)}</pre></p>` : ''}
                ${log.response?.body ? `<p><strong>响应体:</strong> <pre>${log.response.body}</pre></p>` : ''}
            </div>
        </div>
    `;
    
    new bootstrap.Modal(document.getElementById('logDetailModal')).show();
}

function clearAllLogs() {
    if (!confirm('确定要清空所有日志吗？')) {
        return;
    }
    
    clearRequestLogs();
}

// 工具函数
function showLoading(show) {
    const loadingIndicator = document.getElementById('loading-indicator');
    if (loadingIndicator) {
        if (show) {
            loadingIndicator.classList.remove('d-none');
        } else {
            loadingIndicator.classList.add('d-none');
        }
    }
}

function showToast(message, type = 'info') {
    const toastContainer = document.getElementById('toast-container');
    if (!toastContainer) return;
    
    const toastId = 'toast-' + Date.now();
    const iconMap = {
        'success': 'check-circle',
        'error': 'exclamation-circle',
        'warning': 'exclamation-triangle',
        'info': 'info-circle'
    };
    
    const toastHtml = `
        <div id="${toastId}" class="toast" role="alert">
            <div class="toast-header">
                <i class="bi bi-${iconMap[type]} text-${type} me-2"></i>
                <strong class="me-auto">提示</strong>
                <button type="button" class="btn-close" data-bs-dismiss="toast"></button>
            </div>
            <div class="toast-body">
                ${message}
            </div>
        </div>
    `;
    
    toastContainer.insertAdjacentHTML('beforeend', toastHtml);
    
    const toastElement = document.getElementById(toastId);
    const toast = new bootstrap.Toast(toastElement, {
        autohide: true,
        delay: 3000
    });
    
    toast.show();
    
    // 动画结束后删除元素
    toastElement.addEventListener('hidden.bs.toast', () => {
        toastElement.remove();
    });
}

// 快捷键支持
document.addEventListener('keydown', function(event) {
    // Ctrl/Cmd + 数字键切换页面
    if (event.ctrlKey || event.metaKey) {
        switch(event.key) {
            case '1':
                event.preventDefault();
                showDashboard();
                break;
            case '2':
                event.preventDefault();
                showStubs();
                break;
            case '3':
                event.preventDefault();
                showLogs();
                break;
            case 'r':
                event.preventDefault();
                if (currentSection === 'stubs') {
                    loadStubs();
                } else if (currentSection === 'logs') {
                    loadLogs();
                } else {
                    loadDashboard();
                }
                break;
        }
    }
});

// 错误处理
window.addEventListener('unhandledrejection', function(event) {
    console.error('未处理的 Promise 拒绝:', event.reason);
    showToast('发生了一个错误，请刷新页面重试', 'error');
});

// 导出函数供 HTML 调用
window.showDashboard = showDashboard;
window.showStubs = showStubs;
window.showLogs = showLogs;
window.createStub = createStub;
window.loadStubs = loadStubs;
window.searchStubs = searchStubs;
window.clearSearch = clearSearch;
window.toggleStubEnabled = toggleStubEnabled;
window.deleteStub = deleteStub;
window.editStub = editStub;
window.reloadAllStubs = reloadAllStubs;
window.resetWireMock = resetWireMock;
window.clearRequestLogs = clearRequestLogs;
window.showLogDetail = showLogDetail;
window.clearAllLogs = clearAllLogs;
