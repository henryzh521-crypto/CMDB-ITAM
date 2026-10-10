package com.dcim.common.file;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.BucketExistsArgs.Builder;
import io.minio.errors.ErrorResponseException;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"dcim.storage.type"},
    havingValue = "minio"
)
public class MinioFileStorage implements FileStorage {
    private final MinioClient client;
    private final String bucket;

    public MinioFileStorage(
        @Value("${dcim.storage.minio.endpoint}") String endpoint,
        @Value("${dcim.storage.minio.access-key}") String accessKey,
        @Value("${dcim.storage.minio.secret-key}") String secretKey,
        @Value("${dcim.storage.minio.bucket}") String bucket
    ) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    private void ensureBucket() throws Exception {
        boolean exists = this.client.bucketExists((BucketExistsArgs)((Builder)BucketExistsArgs.builder().bucket(this.bucket)).build());
        if (!exists) {
            this.client.makeBucket((MakeBucketArgs)((io.minio.MakeBucketArgs.Builder)MakeBucketArgs.builder().bucket(this.bucket)).build());
        }
    }

    private static boolean isNoSuchKey(ErrorResponseException e) {
        return "NoSuchKey".equals(e.errorResponse() != null ? e.errorResponse().code() : null);
    }

    @Override
    public void store(String key, InputStream in, long size, String contentType) throws Exception {
        this.ensureBucket();
        this.client
            .putObject(
                (PutObjectArgs)((io.minio.PutObjectArgs.Builder)((io.minio.PutObjectArgs.Builder)PutObjectArgs.builder().bucket(this.bucket)).object(key))
                    .stream(in, size, -1L)
                    .contentType(contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream")
                    .build()
            );
    }

    @Override
    public InputStream open(String key) throws Exception {
        try {
            return this.client
                .getObject(
                    (GetObjectArgs)((io.minio.GetObjectArgs.Builder)((io.minio.GetObjectArgs.Builder)GetObjectArgs.builder().bucket(this.bucket)).object(key))
                        .build()
                );
        } catch (ErrorResponseException var3) {
            if (isNoSuchKey(var3)) {
                throw new IOException("文件不存在：" + key);
            } else {
                throw var3;
            }
        }
    }

    @Override
    public void delete(String key) throws Exception {
        this.client
            .removeObject(
                (RemoveObjectArgs)((io.minio.RemoveObjectArgs.Builder)((io.minio.RemoveObjectArgs.Builder)RemoveObjectArgs.builder().bucket(this.bucket))
                        .object(key))
                    .build()
            );
    }

    @Override
    public boolean exists(String key) {
        try {
            this.client
                .statObject(
                    (StatObjectArgs)((io.minio.StatObjectArgs.Builder)((io.minio.StatObjectArgs.Builder)StatObjectArgs.builder().bucket(this.bucket))
                            .object(key))
                        .build()
                );
            return true;
        } catch (ErrorResponseException var3) {
            // 修复：反编译残留 `isNoSuchKey(var3) ? false : false`——对象不存在返回 false，
            // 其他服务端错误应向上抛出而不是一律误判为「文件不存在」
            if (isNoSuchKey(var3)) {
                return false;
            }
            throw new IllegalStateException("MinIO statObject 失败：" + key, var3);
        } catch (Exception var4) {
            return false;
        }
    }
}
