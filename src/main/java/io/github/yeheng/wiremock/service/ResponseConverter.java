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

    private static final String NO_MATCH_MESSAGE = """
        {"error": "No matching stub", "status": 404, "message": "No stub matching the request was found"}
        """;

    public void convert(Response wiremockResponse, HttpServletResponse servletResponse)
            throws IOException {

        servletResponse.setStatus(wiremockResponse.getStatus());

        writeHeaders(wiremockResponse, servletResponse);
        writeBody(wiremockResponse, servletResponse);
    }

    private void writeHeaders(Response wiremockResponse, HttpServletResponse servletResponse) {
        HttpHeaders headers = wiremockResponse.getHeaders();
        if (headers != null) {
            for (HttpHeader header : headers.all()) {
                String headerName = header.key();
                for (String headerValue : header.values()) {
                    servletResponse.addHeader(headerName, headerValue);
                }
            }
        }
    }

    private void writeBody(Response wiremockResponse, HttpServletResponse servletResponse) throws IOException {
        byte[] body = wiremockResponse.getBody();
        log.debug("WireMock响应: status={}, bodyLength={}", wiremockResponse.getStatus(),
                body != null ? body.length : "null");

        if (body != null && body.length > 0) {
            servletResponse.getOutputStream().write(body);
        } else if (wiremockResponse.getStatus() == 404) {
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.getOutputStream().write(NO_MATCH_MESSAGE.getBytes());
        }
    }
}
