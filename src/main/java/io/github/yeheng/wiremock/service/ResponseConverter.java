package io.github.yeheng.wiremock.service;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseConverter {

    public void convert(Response wiremockResponse, HttpServletResponse servletResponse)
            throws IOException {

        servletResponse.setStatus(wiremockResponse.getStatus());

        HttpHeaders headers = wiremockResponse.getHeaders();
        if (headers != null) {
            for (HttpHeader header : headers.all()) {
                String headerName = header.key();
                for (String headerValue : header.values()) {
                    servletResponse.addHeader(headerName, headerValue);
                }
            }
        }

        byte[] body = wiremockResponse.getBody();
        log.debug("WireMock响应: status={}, bodyLength={}", wiremockResponse.getStatus(),
                body != null ? body.length : "null");
        if (body != null && body.length > 0) {
            servletResponse.getOutputStream().write(body);
            servletResponse.getOutputStream().flush();
        } else if (wiremockResponse.getStatus() == 404) {
            String jsonBody = "{\"error\": \"No matching stub\", \"status\": 404, \"message\": \"No stub matching the request was found\"}";
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.getOutputStream().write(jsonBody.getBytes());
            servletResponse.getOutputStream().flush();
        }
    }
}
