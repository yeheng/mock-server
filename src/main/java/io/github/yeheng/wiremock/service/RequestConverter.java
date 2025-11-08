package io.github.yeheng.wiremock.service;

import com.github.tomakehurst.wiremock.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;

/**
 * 请求转换器
 * 将HttpServletRequest转换为WireMock Request对象
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestConverter {

    /**
     * 预分配的头部集合初始容量
     */
    private static final int INITIAL_HEADER_CAPACITY = 32;

    public Request convert(HttpServletRequest servletRequest) throws IOException {
        String absoluteUrl = buildAbsoluteUrl(servletRequest);
        HttpHeaders headers = buildHttpHeaders(servletRequest);
        byte[] body = readRequestBody(servletRequest);
        RequestMethod method = RequestMethod.fromString(servletRequest.getMethod());
        String clientIp = servletRequest.getRemoteAddr();
        String protocol = servletRequest.getProtocol();

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

    private String buildAbsoluteUrl(HttpServletRequest request) {
        StringBuilder urlBuilder = new StringBuilder(request.getRequestURL().toString());
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            urlBuilder.append('?').append(queryString);
        }
        return urlBuilder.toString();
    }

    private HttpHeaders buildHttpHeaders(HttpServletRequest request) {
        var headers = new java.util.ArrayList<HttpHeader>(INITIAL_HEADER_CAPACITY);

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValues = request.getHeaders(headerName);
            if (headerValues == null || !headerValues.hasMoreElements()) {
                continue;
            }

            // 使用Collections.list转换Enumeration，避免手动循环
            var values = java.util.Collections.list(headerValues);
            headers.add(new HttpHeader(headerName, values));
        }

        return new HttpHeaders(headers);
    }

    private byte[] readRequestBody(HttpServletRequest request) throws IOException {
        return request.getInputStream().readAllBytes();
    }
}
