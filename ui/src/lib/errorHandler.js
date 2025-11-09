/**
 * 全局 API 错误处理工具
 */

// 错误类型
export const ErrorTypes = {
  NETWORK: 'NETWORK_ERROR',
  AUTH: 'AUTH_ERROR',
  VALIDATION: 'VALIDATION_ERROR',
  SERVER: 'SERVER_ERROR',
  UNKNOWN: 'UNKNOWN_ERROR',
}

// 错误消息映射
const ErrorMessages = {
  [ErrorTypes.NETWORK]: '网络连接失败，请检查网络设置',
  [ErrorTypes.AUTH]: '身份验证失败，请重新登录',
  [ErrorTypes.VALIDATION]: '数据验证失败，请检查输入内容',
  [ErrorTypes.SERVER]: '服务器错误，请稍后重试',
  [ErrorTypes.UNKNOWN]: '发生未知错误，请重试',
}

/**
 * 解析 HTTP 错误状态码
 * @param {Response} response - fetch 响应对象
 * @returns {string} 错误类型
 */
export function parseHttpError(response) {
  const status = response.status

  if (status === 0) {
    return ErrorTypes.NETWORK
  }
  if (status === 401 || status === 403) {
    return ErrorTypes.AUTH
  }
  if (status === 400 || status === 422) {
    return ErrorTypes.VALIDATION
  }
  if (status >= 500) {
    return ErrorTypes.SERVER
  }
  return ErrorTypes.UNKNOWN
}

/**
 * 处理 API 错误
 * @param {Error} error - 错误对象
 * @param {Response} response - 可选的响应对象
 * @returns {Object} 标准化错误信息
 */
export function handleApiError(error, response = null) {
  // 如果是 Response 对象，提取状态和消息
  if (error instanceof Response) {
    response = error
    error = new Error(`HTTP ${error.status}`)
  }

  // 解析错误类型
  let errorType = ErrorTypes.UNKNOWN
  if (response) {
    errorType = parseHttpError(response)
  } else if (error.name === 'TypeError' && error.message.includes('fetch')) {
    errorType = ErrorTypes.NETWORK
  }

  // 构建错误信息
  const errorInfo = {
    type: errorType,
    message: error.message || ErrorMessages[errorType],
    originalError: error,
    response: response,
    timestamp: new Date().toISOString(),
  }

  // 记录错误
  console.error('[API Error]', errorInfo)

  // 可以在这里添加错误上报逻辑
  // 例如发送到监控服务
  reportError(errorInfo)

  return errorInfo
}

/**
 * 错误上报（可扩展）
 * @param {Object} errorInfo - 错误信息
 */
function reportError(errorInfo) {
  // TODO: 集成错误监控服务
  // 例如 Sentry, LogRocket 等
  // if (import.meta.env.PROD) {
  //   Sentry.captureException(errorInfo.originalError)
  // }
}

/**
 * 显示错误提示
 * @param {Object} errorInfo - 错误信息
 * @param {Function} showNotification - 显示通知的函数
 */
export function showErrorNotification(errorInfo, showNotification) {
  if (typeof showNotification === 'function') {
    showNotification({
      type: 'error',
      title: '操作失败',
      message: errorInfo.message,
    })
  } else {
    // 使用 alert 作为后备
    alert(`错误: ${errorInfo.message}`)
  }
}

/**
 * 检查错误是否可重试
 * @param {Object} errorInfo - 错误信息
 * @returns {boolean} 是否可重试
 */
export function isRetryableError(errorInfo) {
  // 网络错误和服务器错误可以重试
  return errorInfo.type === ErrorTypes.NETWORK || errorInfo.type === ErrorTypes.SERVER
}

/**
 * 创建带错误处理的 API 调用包装器
 * @param {Function} apiCall - API 调用函数
 * @param {Object} options - 选项
 * @returns {Function} 包装后的函数
 */
export function withErrorHandling(apiCall, options = {}) {
  const { showNotification = true } = options

  return async function (...args) {
    try {
      return await apiCall(...args)
    } catch (error) {
      const errorInfo = handleApiError(error)

      if (showNotification) {
        showErrorNotification(errorInfo, options.notificationHandler)
      }

      throw errorInfo
    }
  }
}

/**
 * 异步错误处理装饰器
 * @param {Function} fn - 原始函数
 * @returns {Function} 包装后的函数
 */
export function asyncErrorHandler(fn) {
  return async function (...args) {
    try {
      return await fn.apply(this, args)
    } catch (error) {
      console.error(`Error in ${fn.name}:`, error)
      throw error
    }
  }
}
