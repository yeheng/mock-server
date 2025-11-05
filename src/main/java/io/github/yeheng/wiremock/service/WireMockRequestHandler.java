package io.github.yeheng.wiremock.service;

import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer;
import com.github.tomakehurst.wiremock.http.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 负责处理请求转换与响应写回的组件
 * 与 Stub CRUD 解耦
 */
@Slf4j
@Service
public class WireMockRequestHandler {

    /**
     * 将 Servlet 请求处理为 WireMock 响应并写回
     */
    public void handle(HttpServletRequest servletRequest,
                       HttpServletResponse servletResponse,
                       DirectCallHttpServer directCallServer) throws IOException {
        try {
            // 1. 转换为 WireMock Request
            Request wiremockRequest = toWireMockRequest(servletRequest);

            // 2. 调用直连服务器进行匹配
            Response wiremockResponse = directCallServer.stubRequest(wiremockRequest);

            // 3. 写回响应
            fromWireMockResponse(wiremockResponse, servletResponse);

        } catch (Exception e) {
            log.error("处理WireMock请求时出错", e);
            servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setCharacterEncoding("UTF-8");
            servletResponse.getWriter()
                    .write("{\"error\": \"Internal server error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 将 WireMock Response 转换并写入 Servlet Response
     */
    private void fromWireMockResponse(Response wiremockResponse, HttpServletResponse servletResponse)
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
        if (body != null && body.length > 0) {
            servletResponse.getOutputStream().write(body);
            servletResponse.getOutputStream().flush();
        } else {
            // 处理空响应体的情况，特别是404错误
            if (wiremockResponse.getStatus() == 404) {
                String jsonBody = "{\"error\": \"No matching stub\", \"status\": 404, \"message\": \"No stub matching the request was found\"}";
                servletResponse.setContentType("application/json;charset=UTF-8");
                servletResponse.setCharacterEncoding("UTF-8");
                servletResponse.getWriter().write(jsonBody);
                servletResponse.getWriter().flush();
            }
        }
    }

    /**
     * 将 Servlet 请求转换为 WireMock Request 对象
     */
    private Request toWireMockRequest(HttpServletRequest servletRequest) throws IOException {
        // 1. 提取请求方法
        RequestMethod method = RequestMethod.fromString(servletRequest.getMethod());

        // 2. 构建绝对 URL
        String absoluteUrl = servletRequest.getRequestURL().toString();
        String queryString = servletRequest.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            absoluteUrl = absoluteUrl + "?" + queryString;
        }

        // 3. 提取 Headers - 转换为 WireMock 的 HttpHeaders
        List<HttpHeader> headerList = new ArrayList<>();
        Enumeration<String> headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValues = servletRequest.getHeaders(headerName);
            List<String> values = Collections.list(headerValues);
            headerList.add(new HttpHeader(headerName, values));
        }
        HttpHeaders headers = new HttpHeaders(headerList);

        // 4. 提取请求体
        byte[] body;
        try (InputStream inputStream = servletRequest.getInputStream()) {
            body = inputStream.readAllBytes();
        }

        // 5. 获取客户端 IP
        String clientIp = servletRequest.getRemoteAddr();

        // 6. 获取协议
        String protocol = servletRequest.getProtocol();

        // 7. 使用 ImmutableRequest.Builder 构造 Request 对象
        return ImmutableRequest.create()
                .withAbsoluteUrl(absoluteUrl)
                .withMethod(method)
                .withProtocol(protocol)
                .withClientIp(clientIp)
                .withHeaders(headers)
                .withBody(body)
                .withBrowserProxyRequest(false)
                .build();
    }
}