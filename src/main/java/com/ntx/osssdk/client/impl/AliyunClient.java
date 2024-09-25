package com.ntx.osssdk.client.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.model.*;
import com.ntx.osssdk.client.OssClient;
import com.ntx.osssdk.properties.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @ClassName AliyunClient
 * @Author ntx
 * @Description 阿里云oss
 */
@Slf4j
public class AliyunClient implements OssClient {
    @Resource
    private Properties properties;

    @Override
    public String upload(MultipartFile file) throws Exception {
        //判断文件是否为空
        if (file == null || file.getSize() == 0 || file.isEmpty()) {
            log.error("==> 上传文件异常，文件大小为空...");
            throw new RuntimeException("文件大小为空");
        }
        String fileName = getFileName(file);
        CredentialsProvider credentialsProvider = new DefaultCredentialProvider(properties.getAccessKey(), properties.getSecretKey());
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(properties.getEndpoint(), credentialsProvider);
        String url = "";
        try {
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(properties.getBucket(), fileName, file.getInputStream());
            // 创建PutObject请求。
            PutObjectResult result = ossClient.putObject(putObjectRequest);
            url = getUrl(fileName);
            log.info("==>上传文件至阿里云成功，访问路径：{}", url);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
        return url;
    }

    @Override
    public boolean download(String downloadPath, String objectName) {
        if (downloadPath.isEmpty() || !StringUtils.hasLength(objectName)) {
            throw new RuntimeException("下载文件参数不全！");
        }
        if (!new File(downloadPath).isDirectory()) {
            throw new RuntimeException("本地下载路径必须是一个文件夹或者文件路径！");
        }
        String fileName = objectName.substring(objectName.lastIndexOf("/"));
        downloadPath += fileName;
        CredentialsProvider credentialsProvider = new DefaultCredentialProvider(properties.getAccessKey(), properties.getSecretKey());
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(properties.getEndpoint(), credentialsProvider);
        String remotePath = getUrl("");
        objectName = objectName.replace(remotePath,"");
        try {
            ossClient.getObject(new GetObjectRequest(properties.getBucket(), objectName), new File(downloadPath));
            log.info("==>下载成功，访问路径：{}", downloadPath);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
        return true;
    }

    @Override
    public String uploadFile(MultipartFile file) throws Exception {
        //判断文件是否为空
        if (file == null || file.getSize() == 0 || file.isEmpty()) {
            log.error("==> 上传文件异常，文件大小为空...");
            throw new RuntimeException("文件大小为空");
        }
        String fileName = getFileName(file);
        CredentialsProvider credentialsProvider = new DefaultCredentialProvider(properties.getAccessKey(), properties.getSecretKey());
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(properties.getEndpoint(), credentialsProvider);
        String url = "";
        try {
            // 创建InitiateMultipartUploadRequest对象。
            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(properties.getBucket(), fileName);

            // 如果需要在初始化分片时设置请求头，请参考以下示例代码。
            ObjectMetadata metadata = new ObjectMetadata();
            // 根据文件自动设置ContentType。如果不设置，ContentType默认值为application/oct-srream。
            if (metadata.getContentType() == null) {
                metadata.setContentType(file.getContentType());
            }

            // 初始化分片。
            InitiateMultipartUploadResult upresult = ossClient.initiateMultipartUpload(request);
            // 返回uploadId。
            String uploadId = upresult.getUploadId();
            // 根据uploadId执行取消分片上传事件或者列举已上传分片的操作。
            // 如果您需要根据uploadId执行取消分片上传事件的操作，您需要在调用InitiateMultipartUpload完成初始化分片之后获取uploadId。
            // 如果您需要根据uploadId执行列举已上传分片的操作，您需要在调用InitiateMultipartUpload完成初始化分片之后，且在调用CompleteMultipartUpload完成分片上传之前获取uploadId。
            // System.out.println(uploadId);

            // partETags是PartETag的集合。PartETag由分片的ETag和分片号组成。
            List<PartETag> partETags = new ArrayList<PartETag>();
            // 每个分片的大小，用于计算文件有多少个分片。单位为字节。
            final long partSize = 5 * 1024 * 1024L;   //5 MB。

            // 根据上传的数据大小计算分片数。以本地文件为例，说明如何通过File.length()获取上传数据的大小。
            long fileLength = file.getSize();
            int partCount = (int) (fileLength / partSize);
            if (fileLength % partSize != 0) {
                partCount++;
            }
            // 遍历分片上传。
            for (int i = 0; i < partCount; i++) {
                long startPos = i * partSize;
                long curPartSize = (i + 1 == partCount) ? (fileLength - startPos) : partSize;
                UploadPartRequest uploadPartRequest = new UploadPartRequest();
                uploadPartRequest.setBucketName(properties.getBucket());
                uploadPartRequest.setKey(fileName);
                uploadPartRequest.setUploadId(uploadId);
                // 设置上传的分片流。
                // 以本地文件为例说明如何创建FIleInputstream，并通过InputStream.skip()方法跳过指定数据。
                InputStream instream = file.getInputStream();
                instream.skip(startPos);
                uploadPartRequest.setInputStream(instream);
                // 设置分片大小。除了最后一个分片没有大小限制，其他的分片最小为100 KB。
                uploadPartRequest.setPartSize(curPartSize);
                // 设置分片号。每一个上传的分片都有一个分片号，取值范围是1~10000，如果超出此范围，OSS将返回InvalidArgument错误码。
                uploadPartRequest.setPartNumber(i + 1);
                // 每个分片不需要按顺序上传，甚至可以在不同客户端上传，OSS会按照分片号排序组成完整的文件。
                UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);
                // 每次上传分片之后，OSS的返回结果包含PartETag。PartETag将被保存在partETags中。
                partETags.add(uploadPartResult.getPartETag());
            }
            // 创建CompleteMultipartUploadRequest对象。
            // 在执行完成分片上传操作时，需要提供所有有效的partETags。OSS收到提交的partETags后，会逐一验证每个分片的有效性。当所有的数据分片验证通过后，OSS将把这些分片组合成一个完整的文件。
            CompleteMultipartUploadRequest completeMultipartUploadRequest =
                    new CompleteMultipartUploadRequest(properties.getBucket(), fileName, uploadId, partETags);
            // 完成分片上传。
            CompleteMultipartUploadResult completeMultipartUploadResult = ossClient.completeMultipartUpload(completeMultipartUploadRequest);
            url = getUrl(fileName);
            log.info("==>上传文件至阿里云成功，访问路径：{}", url);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
        return url;
    }

    /**
     * 删除碎片文件
     * @param uploadId
     * @param ossClient
     * @param fileName
     */
    private void deleteTempFile(String uploadId, OSS ossClient, String fileName) {
        // 取消分片上传。
        AbortMultipartUploadRequest abortMultipartUploadRequest =
                new AbortMultipartUploadRequest(properties.getBucket(), fileName, uploadId);
        ossClient.abortMultipartUpload(abortMultipartUploadRequest);
    }


    /**
     * 获取文件名称
     *
     * @param file 上传的文件
     * @return
     */
    private static String getFileName(MultipartFile file) {
        //文件的原始名称
        String originalFilename = file.getOriginalFilename();
        //生成存储对象的名称（将UUID中的“-"替换成“”）
        String key = UUID.randomUUID().toString().replace("-", "");
        //获取文件后缀
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        // 获取当前日期
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/");
        String dateString = today.format(formatter);
        //拼接上下文后缀
        return String.format("%S%s%s", dateString, key, suffix);
    }

    /**
     * 获取上传文件后的访问路径
     *
     * @param objectName
     * @return
     */
    private String getUrl(String objectName) {
        String endpoint = properties.getEndpoint();
        int secondSlashIndex = endpoint.indexOf("/", endpoint.indexOf("/") + 1);
        endpoint = endpoint.substring(0, secondSlashIndex + 1) + properties.getBucket() + '.' + endpoint.substring(secondSlashIndex + 1);
        //返回文件的访问链接
        String url = String.format("%s/%s", endpoint, objectName);
        return url;
    }
}
