package io.github.yeheng.wiremock.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 正则表达式缓存
 * 避免重复编译相同的正则模式，提升匹配性能
 */
public class RegexCache {
    private static final ConcurrentHashMap<String, Pattern> CACHE = new ConcurrentHashMap<>();

    private RegexCache() {}

    public static Pattern getPattern(String regex) {
        return CACHE.computeIfAbsent(regex, Pattern::compile);
    }

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{[^}]+}");
    private static final Pattern URL_ESCAPE_PATTERN = Pattern.compile("[.*+?^${}()|\\\\]");

    public static String escapeRegex(String text) {
        if (text == null) {
            return "";
        }
        return URL_ESCAPE_PATTERN.matcher(text).replaceAll("\\\\$&");
    }

    public static String convertPathTemplateToRegex(String path) {
        if (path == null || path.isEmpty()) {
            return ".*";
        }
        return PATH_PARAM_PATTERN.matcher(path)
                .replaceAll("[^/]+");
    }
}
