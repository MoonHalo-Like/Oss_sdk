package com.ntx.osssdk;

import com.ntx.osssdk.client.OssClient;
import com.ntx.osssdk.client.OssClientFactory;
import com.ntx.osssdk.properties.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * @ClassName OssSdkClientConfig
 * @Author ntx
 */
// 通过 @Configuration 注解,将该类标记为一个配置类,告诉 Spring 这是一个用于配置的类1811251804847943682_0.5564212530333092


@Configuration
@ComponentScan
public class OssSdkClientConfig {
    @Resource
    private Properties properties;

    @Bean
    public OssClient ossClient(){
        return OssClientFactory.newInstance(properties.getType());
    }

}
