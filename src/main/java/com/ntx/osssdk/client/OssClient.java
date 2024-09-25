package com.ntx.osssdk.client;

import org.springframework.web.multipart.MultipartFile;

/**
 * @ClassName OssClient
 * @Author ntx
 * @Description Oss存储客户端接口定义
 */
public interface OssClient {

    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @return url
     */
    String upload(MultipartFile file) throws Exception;

    /**
     * 下载文件
     *
     * @param downloadPath 下载路径
     * @param objectName   文件名称
     * @return true/false
     */
    boolean download(String downloadPath, String objectName) throws Exception;

    /**
     * 分片上传文件
     *
     * @param file 上传的文件
     * @return url
     * @throws Exception 异常
     */
    String uploadFile(MultipartFile file) throws Exception;
}
