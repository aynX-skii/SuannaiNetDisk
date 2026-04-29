package com.suannai.netdisk.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppSpaController {

    @GetMapping({"/app", "/app/", "/app/{path:^(?!assets|favicon\\.ico).*$}", "/app/{path:^(?!assets|favicon\\.ico).*$}/**"})
    public String app() {
        return "forward:/app/index.html";
    }
}
