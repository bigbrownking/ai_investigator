package org.di.digital.config.db;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioBucketInitilizer implements ApplicationRunner {

    private final MinioClient minioClient;

    @Value("${minio.bucket.name:cases}")
    private String bucketName;

    @Override
    public void run(ApplicationArguments args){
        try{
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
            
                if(!exists){
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                    log.info("Created MinIO bucket: {}", bucketName);

                }else{
                    log.info("MinIO bucket already exists: {}", bucketName);
                }
        }catch(Exception e){
            log.info("Failed to ensure MinIO bucket {}: {}", bucketName, e.getMessage(),e);
        }

    }
    
}
