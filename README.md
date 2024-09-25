# Oss-sdk使用说明

### 介绍

整合Oss存储服务，快速切换存储服务

功能：

```
1、上传文件
2、分片上传文件
3、下载文件
```

### 快速上手

```
        <dependency>
                <groupId>io.github.ntxlike</groupId>
            <artifactId>oss-sdk</artifactId>
            <version>0.0.4</version>
        </dependency>
```

### 配置yml

```
oss:
  client:
    type: minio #存储服务类型：minio、aliyun、qiniu
    endpoint: http://127.0.0.1:9000 #地址
    bucket: myblog #桶名
    access-key: adminminio 
    secret-key: adminminio
```

### 使用方法

```
注入客户端
@Resource
private OssClient ossClient;
```

```
调用方法
    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @return url
     */
    String upload(MultipartFile file);

    /**
     * 下载文件
     *
     * @param downloadPath 下载路径
     * @param objectName   文件名称
     * @return true/false
     */
    boolean download(String downloadPath, String objectName);

    /**
     * 分片上传文件
     *
     * @param file 上传的文件
     * @return url

     */
    String uploadFile(MultipartFile file) ;
```

