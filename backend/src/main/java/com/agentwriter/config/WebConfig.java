package com.agentwriter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Web 静态资源映射。
 *
 * <p>资源图库的本地图片存放在 {@code {storage-directory}/gallery/} 目录，
 * 通过 {@code /media/files/**} 对外提供访问（素材 {@code url} 即该前缀相对路径）。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ImageProperties imageProperties;

    public WebConfig(ImageProperties imageProperties) {
        this.imageProperties = imageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String storageDirectory = imageProperties.getStorageDirectory();
        if (storageDirectory != null && !storageDirectory.isBlank()) {
            String location = Path.of(storageDirectory).toAbsolutePath().normalize().toUri().toString();
            registry.addResourceHandler("/media/files/**")
                    .addResourceLocations(location);
        }
    }
}
