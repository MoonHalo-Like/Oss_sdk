package com.ntx.osssdk.config;

import com.ntx.osssdk.properties.Properties;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * @ClassName MinioConfig
 * @Author ntx
 * @Description minio配置
 */
@Configuration
public class MinioConfig {

    @Resource
    private Properties properties;

    @Bean
    public MinioClient minioClient(){
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
