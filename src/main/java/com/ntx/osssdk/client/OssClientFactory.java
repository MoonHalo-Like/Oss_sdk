package com.ntx.osssdk.client;

import com.ntx.osssdk.client.impl.AliyunClient;
import com.ntx.osssdk.client.impl.MinioClients;
import com.ntx.osssdk.client.impl.QiniuClients;

/**
 * @ClassName OssClientFactory
 * @Author ntx
 * @Description Oss客户端工厂
 */
public class OssClientFactory {
    public static OssClient newInstance(String type) {
        switch (type) {
            case "minio":
                return new MinioClients();
            case "aliyun":
                return new AliyunClient();
            case "qiniu":
                return new QiniuClients();
            default:
                return new MinioClients();
        }
    }
}
