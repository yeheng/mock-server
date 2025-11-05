package io.github.yeheng.wiremock.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 回退控制器
 * 将非 /api 与 /admin 等后端路径之外的前端路由，统一转发到 index.html
 */
@Controller
public class SpaController {

    // 根路径与所有非 api/admin/actuator/h2-console/__admin 的路径，统一回退到前端入口
    @GetMapping({
            "/",
            "/{path:^(?!api|admin|actuator|h2-console|__admin|mappings|files).*$}",
            "/{path:^(?!api|admin|actuator|h2-console|__admin|mappings|files).*$}/**"
    })
    public String index() {
        return "forward:/index.html";
    }
}