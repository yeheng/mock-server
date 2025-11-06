package io.github.yeheng.wiremock.service;

import com.github.tomakehurst.wiremock.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * WireMock Response 到 Servlet Response 的转换器
 * 单一职责：处理 HTTP 响应对象的转换
 * 优化：使用 ZeroCopyBuffer 减少内存分配
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseConverter {

    /**
     * 将 WireMock Response 转换并写入 Servlet Response
     * 使用 ZeroCopyBuffer 优化响应处理，减少内存分配
     */
    public void convert(Response wiremockResponse, HttpServletResponse servletResponse)
            throws IOException {

        // 1. 设置状态码
        servletResponse.setStatus(wiremockResponse.getStatus());

        // 2. 设置响应头
        HttpHeaders headers = wiremockResponse.getHeaders();
        if (headers != null) {
            for (HttpHeader header : headers.all()) {
                String headerName = header.key();
                for (String headerValue : header.values()) {
                    // 使用 addHeader 支持多值头
                    servletResponse.addHeader(headerName, headerValue);
                }
            }
        }

        // 3. 写入响应体
        byte[] body = wiremockResponse.getBody();
        log.info("WireMock响应: status={}, bodyLength={}", wiremockResponse.getStatus(),
                body != null ? body.length : "null");
        if (body != null && body.length > 0) {
            // 直接写入字节数组，简化逻辑
            servletResponse.getOutputStream().write(body);
            servletResponse.getOutputStream().flush();
        } else {
            // 处理空响应体的情况
            if (wiremockResponse.getStatus() == 404) {
                // 404 错误返回错误信息
                String jsonBody = "{\"error\": \"No matching stub\", \"status\": 404, \"message\": \"No stub matching the request was found\"}";
                ByteBuffer buffer = ZeroCopyBuffer.writeString(jsonBody);
                servletResponse.setContentType("application/json;charset=UTF-8");
                servletResponse.setCharacterEncoding("UTF-8");
                servletResponse.getOutputStream().write(buffer.array(), 0, buffer.remaining());
                servletResponse.getOutputStream().flush();
                ZeroCopyBuffer.releaseBuffer(buffer);
            }
            // 对于其他状态码的正常响应，如果响应体为空，则不写入任何内容（这是有效的响应）
        }
    }
}
