package com.ntx.osssdk.client.impl;

import com.ntx.osssdk.client.OssClient;
import com.ntx.osssdk.properties.Properties;
import com.ntx.osssdk.utils.MinioUtil;
import io.minio.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @ClassName MinioClient
 * @Author ntx
 * @Description minio client
 */
@Slf4j
public class MinioClients implements OssClient {
    //minio每个分片不能低于5MB，最后一个分片可以不管 13MB文件可分成3个分片 5MB 5MB 3MB
    private static final int PART_SIZE = 5 * 1024 * 1024;
    @Resource
    private MinioClient minioClient;

    @Resource
    private Properties properties;
    @Resource
    private MinioUtil minioUtil;

    /**
     * 上传文件
     *
     * @param file 文件
     * @return url
     */
    @Override
    public String upload(MultipartFile file) throws Exception {
        //判断文件是否为空
        if (file == null || file.getSize() == 0 || file.isEmpty()) {
            log.error("==> 上传文件异常，文件大小为空...");
            throw new RuntimeException("文件大小为空");
        }
        String objectName = getFileName(file);

        log.info("==>开始上传文件至minio，ObjectName:{}", objectName);
        //文件的Content-Type
        String contentType = file.getContentType();
        //上传文件至minio
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(contentType)
                .build());
        return getUrl(objectName);
    }


    /**
     * 文件下载到指定路径
     *
     * @param downloadPath 下载到本地路径
     * @param objectName   下载的文件名称
     * @return boolean
     */
    @Override
    public boolean download(String downloadPath, String objectName) throws Exception {
        if (downloadPath.isEmpty() || !StringUtils.hasLength(objectName)) {
            throw new RuntimeException("下载文件参数不全！");
        }
        if (!new File(downloadPath).isDirectory()) {
            throw new RuntimeException("本地下载路径必须是一个文件夹或者文件路径！");
        }
        if (!minioUtil.bucketExists(properties.getBucket())) {
            throw new RuntimeException("当前操作的桶不存在！");
        }
        String fileName = objectName.substring(objectName.lastIndexOf("/"));
        downloadPath += fileName;
        //获取文件路径
        String remotePath = String.format("%s/%s/", properties.getEndpoint(),properties.getBucket());
        objectName = objectName.replace(remotePath, "");

        minioClient.downloadObject(
                DownloadObjectArgs.builder()
                        .bucket(properties.getBucket()) //指定是在哪一个桶下载
                        .object(objectName)//是minio中文件存储的名字;本地上传的文件是user.xlsx到minio中存储的是user-minio,那么这里就是user-minio
                        .filename(downloadPath)//需要下载到本地的路径，一定是带上保存的文件名；如 d:\\minio\\user.xlsx
                        .build());
        log.info("==>下载成功，访问路径：{}", downloadPath);
        return true;
    }

    /**
     *
     *  分片上传文件
     * @param file 上传的文件
     * @return 访问地址
     * @throws Exception
     */
    @Override
    public String uploadFile(MultipartFile file) throws Exception {

        String fileName = getFileName(file);//获取文件名称
        long startTime = System.currentTimeMillis() / 1000;
        //获取文件流
        InputStream inputStream = file.getInputStream();
        //获取文件大小
        long fileSize = file.getSize();
        //计算分片数量
        int partCount = (int) (fileSize / PART_SIZE);
        if (fileSize % PART_SIZE > 0) {
            partCount++;
        }
        long partTime = System.currentTimeMillis() / 1000;
        System.out.println("分片耗时" + (partTime - startTime));
        //存放分片流
        List<InputStream> parts = new ArrayList<>();
        //存放分片minio地址
        List<String> fileList = new ArrayList<>();
        //分配分片流
        for (int i = 0; i < partCount; i++) {
            // 每次只需要从原始文件InputStream中读取指定大小的数据即可
            byte[] partData = new byte[PART_SIZE];
            int read = inputStream.read(partData);
            if (read == -1) {
                break; // 文件已经读完了
            }
            // 将读取的数据作为一个新的InputStream添加到parts列表中
            parts.add(new ByteArrayInputStream(partData, 0, read));
        }
        long readTime = System.currentTimeMillis() / 1000;
        System.out.println("读取文件耗时" + (readTime - partTime));
        //上传分片流到minio
        for (int i = 0; i < parts.size(); i++) {
            // 构建每个part的object name
            String partObjectName = fileName + ".part" + i;
            fileList.add(partObjectName);
            InputStream partStream = parts.get(i);
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(partObjectName)
                    .stream(partStream, partStream.available(), -1)
                    .contentType(file.getContentType())
                    .build();
            ObjectWriteResponse objectWriteResponse = minioClient.putObject(args);
            //System.out.println("分片上传结果======++++++"+objectWriteResponse);
        }
        long upLoadTime = System.currentTimeMillis() / 1000;
        System.out.println("上传分片耗时" + (upLoadTime - readTime));
        //关闭主文件输入流和分片输入流
        inputStream.close();
        for (InputStream part : parts) {
            part.close();
        }
        //获取需要合并的分片组装成ComposeSource
        List<ComposeSource> sourceObjectList = new ArrayList<>(fileList.size());
        for (String chunk : fileList) {
            sourceObjectList.add(
                    ComposeSource.builder()
                            .bucket(properties.getBucket())
                            .object(chunk)
                            .build()
            );
        }
        //合并分片
        ComposeObjectArgs composeObjectArgs = ComposeObjectArgs.builder()
                .bucket(properties.getBucket())
                //合并后的文件的objectname
                .object(fileName)
                //指定源文件
                .sources(sourceObjectList)
                .build();
        minioClient.composeObject(composeObjectArgs);
        long mergeTime = System.currentTimeMillis() / 1000;
        System.out.println("合并分片耗时" + (mergeTime - upLoadTime));
        //删除已经上传的分片，组装成DeleteObject
        List<DeleteObject> collect = fileList.stream().map(DeleteObject::new).collect(Collectors.toList());
        //执行删除
        RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder()
                .bucket(properties.getBucket())
                .objects(collect)
                .build();
        Iterable<Result<DeleteError>> results = minioClient.removeObjects(removeObjectsArgs);
        //如果没有下面try的代码，文件史删除不了的，加上下面的代码就可以删除了
        try {
            for (Result<DeleteError> result : results) {
                DeleteError deleteError = result.get();
                System.out.println("error in deleteing object" + deleteError.objectName() + ";" + deleteError.message());
            }
        } catch (Exception e) {
            System.out.println("minio删除文件失败");
            e.printStackTrace();
        }
        long deleteTime = System.currentTimeMillis() / 1000;
        System.out.println("删除分片耗时" + (deleteTime - mergeTime));
        return getUrl(fileName);
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
        //返回文件的访问链接
        String url = String.format("%s/%s/%s", properties.getEndpoint(), properties.getBucket(), objectName);
        log.info("==>上传文件至minio成功，访问路径：{}", url);
        return url;
    }
}
