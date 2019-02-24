package com.cloud.configautoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = ConfigSupportProperties.CONFIG_PREFIX)
public class ConfigSupportProperties {
    public static final String CONFIG_PREFIX = "spring.cloud.config.backup";
    private final String DEFAULT_FILE_NAME = "fallback.properties";
    private boolean enable = false;
    private String fallbackLocation;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getFallbackLocation() {
        return fallbackLocation;
    }

    public void setFallbackLocation(String fallbackLocation) {
        if(fallbackLocation.indexOf(".") == -1){
            this.fallbackLocation += DEFAULT_FILE_NAME;
        }
        this.fallbackLocation = fallbackLocation;
    }
}
