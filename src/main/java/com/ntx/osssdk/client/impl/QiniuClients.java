package com.ntx.osssdk.client.impl;

import com.ntx.osssdk.client.OssClient;
import com.ntx.osssdk.properties.Properties;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.persistent.FileRecorder;
import com.qiniu.util.Auth;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * @ClassName QiniuClient
 * @Author ntx
 * @Description 七牛云对象存储客户端
 */
@Slf4j
public class QiniuClients implements OssClient {
    @Resource
    private Properties properties;

    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @return url
     * @throws Exception 异常
     */
    @Override
    public String upload(MultipartFile file) throws Exception {
        //判断文件是否为空
        if (file == null || file.getSize() == 0 || file.isEmpty()) {
            log.error("==> 上传文件异常，文件大小为空...");
            throw new RuntimeException("文件大小为空");
        }
        String objectName = getFileName(file);
        // 从 MultipartFile 直接获取输入流，避免先保存到本地再上传
        InputStream inputStream = file.getInputStream();
        // 构造一个带指定 Region 对象的配置类
        Configuration zone = new Configuration(Region.autoRegion());//我们刚开始选的是华南地区
        //创建Auth对象，填写ak和sk
        Auth auth = Auth.create(properties.getAccessKey(), properties.getSecretKey());
        //获得上传凭证
        String upToken = auth.uploadToken(properties.getBucket());
        log.debug("==>获取上传凭证成功:" + upToken);
        UploadManager uploadManager = new UploadManager(zone);
        Response response = uploadManager.put(inputStream, objectName, upToken, null, null);
        return getUrl(objectName);
    }

    /**
     * 下载文件
     *
     * @param downloadPath 下载路径
     * @param objectName   文件名称
     * @return
     * @throws Exception
     */
    @Override
    public boolean download(String downloadPath, String objectName) throws Exception {
        if (downloadPath.isEmpty() || !StringUtils.hasLength(objectName)) {
            throw new RuntimeException("下载文件参数不全！");
        }
        if (!new File(downloadPath).isDirectory()) {
            throw new RuntimeException("本地下载路径必须是一个文件夹或者文件路径！");
        }
        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder().url(objectName).build();
        okhttp3.Response resp = null;
        String fileName = objectName.substring(objectName.lastIndexOf("/"));
        try {
            resp = client.newCall(req).execute();
            System.out.println(resp.isSuccessful());
            if (resp.isSuccessful()) {
                ResponseBody body = resp.body();
                InputStream is = body.byteStream();
                byte[] data = readInputStream(is);
                //判断文件夹是否存在，不存在则创建
                File file = new File(downloadPath);
                if (!file.exists() && !file.isDirectory()) {
                    System.out.println("===文件夹不存在===创建====");
                    file.mkdir();
                }
                File imgFile = new File(downloadPath + fileName);
                FileOutputStream fops = new FileOutputStream(imgFile);
                fops.write(data);
                fops.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Unexpected code " + resp);
        }
        return true;
    }

    /**
     * 分片上传文件
     *
     * @param file 上传的文件
     * @return url
     * @throws Exception
     */
    @Override
    public String uploadFile(MultipartFile file) throws Exception {
        //判断文件是否为空
        if (file == null || file.getSize() == 0 || file.isEmpty()) {
            log.error("==> 上传文件异常，文件大小为空...");
            throw new RuntimeException("文件大小为空");
        }
        String objectName = getFileName(file);
        //构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.autoRegion());
        cfg.resumableUploadAPIVersion = Configuration.ResumableUploadAPIVersion.V2;// 指定分片上传版本
        cfg.resumableUploadMaxConcurrentTaskCount = 2;  // 设置分片上传并发，1：采用同步上传；大于1：采用并发上传
        Auth auth = Auth.create(properties.getAccessKey(), properties.getSecretKey());
        String upToken = auth.uploadToken(properties.getBucket());
        String localTempDir = Paths.get(System.getenv("java.io.tmpdir"), properties.getBucket()).toString();
        //设置断点续传文件进度保存目录
        FileRecorder fileRecorder = new FileRecorder(localTempDir);
        UploadManager uploadManager = new UploadManager(cfg, fileRecorder);
        Response response = uploadManager.put(file.getInputStream(), objectName, upToken, null, null);
        return getUrl(objectName);
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
        return String.format("%s%s%s", dateString, key, suffix);
    }


    /**
     * 获取上传文件后的访问路径
     *
     * @param objectName
     * @return
     */
    private String getUrl(String objectName) {
        //返回文件的访问链接
        String url = String.format("%s/%s", properties.getEndpoint(), objectName);
        log.info("==>上传文件至七牛云成功，访问路径：{}", url);
        return url;
    }

    /**
     * 读取字节输入流内容
     *
     * @param is
     * @return
     */
    private static byte[] readInputStream(InputStream is) {
        ByteArrayOutputStream writer = new ByteArrayOutputStream();
        byte[] buff = new byte[1024 / 2];
        int len = 0;
        try {
            while ((len = is.read(buff)) != -1) {
                writer.write(buff, 0, len);
            }
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return writer.toByteArray();
    }
}
