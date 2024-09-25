package com.ntx.osssdk.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @ClassName properties
 * @Author ntx
 * @Description 配置文件
 */
@ConfigurationProperties("oss.client")
@Data
@Component
public class Properties {

    /**
     * 存储服务类型
     */
    private String type;
    /**
     * accessKey
     */
    private String accessKey;

    /**
     * secretKey
     */
    private String secretKey;

    /**
     * 区域
     */
    private String endpoint;

    /**
     * 桶名
     */
    private String bucket;
}
