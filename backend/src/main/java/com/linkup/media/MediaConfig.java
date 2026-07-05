package com.linkup.media;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Builds the {@link S3Presigner} that mints short-lived upload/download URLs (Day 10).
 *
 * We only ever presign — we never stream bytes through the app — so a plain presigner (no
 * S3Client, no startup connection to MinIO) is all we need. Path-style access is forced because
 * MinIO doesn't do virtual-host-style buckets ({@code bucket.host}); it wants {@code host/bucket}.
 */
@Configuration
public class MediaConfig {

    @Bean
    public S3Presigner s3Presigner(MediaProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)   // MinIO: host/bucket, not bucket.host
                        .build())
                .build();
    }
}
