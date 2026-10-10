package com.dcim.common;

import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 构建版本信息（v3.3.1 新增）。
 *
 * <p>v3.3.0 及以前后端不报告自身版本：把新包解压覆盖到旧目录后，启动脚本按文件名排序选中了旧的
 * dcim-platform-3.2.0.jar，运行的仍是旧代码，却无从察觉。现在启动日志第一行和 /api/healthz 都带版本号，
 * 版本取自 jar 内 dcim-build.properties。
 */
@Component
public class BuildInfo implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(BuildInfo.class);
    private static final Properties PROPS = load();

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/dcim-build.properties")) {
            if (in != null) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 缺文件时报 unknown，不影响启动
        }
        return p;
    }

    public static String version() {
        return PROPS.getProperty("version", "unknown");
    }

    public static String buildTime() {
        return PROPS.getProperty("built", "unknown");
    }

    public static String jarLocation() {
        try {
            return String.valueOf(BuildInfo.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("DCIM Platform {} 已就绪（构建 {}，jar：{}）", version(), buildTime(), jarLocation());
    }
}
