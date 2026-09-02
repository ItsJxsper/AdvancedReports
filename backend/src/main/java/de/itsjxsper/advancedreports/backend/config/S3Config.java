package de.itsjxsper.advancedreports.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Slf4j
@Configuration
@Profile("s3")
public class S3Config {

    @Value("${aws.s3.region:eu-central-1}")
    private String region;

    @Value("${aws.s3.endpoint-url:}")
    private String endpointUrl;

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    /**
     * Used only for metadata operations - verifying and deleting objects. Screenshot bytes never pass
     * through this client; clients transfer them directly against the URLs signed by the presigner.
     */
    @Bean
    @ConditionalOnProperty(prefix = "aws.s3", name = "bucket")
    public S3Client s3Client() {
        var clientBuilder = S3Client.builder()
                .region(resolveRegion())
                .credentialsProvider(resolveCredentialsProvider());

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            clientBuilder.endpointOverride(URI.create(endpointUrl));
            clientBuilder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        }

        return clientBuilder.build();
    }

    /**
     * Signs the short-lived upload and download URLs the clients use to talk to S3 directly.
     */
    @Bean
    @ConditionalOnProperty(prefix = "aws.s3", name = "bucket")
    public S3Presigner s3Presigner() {
        var presignerBuilder = S3Presigner.builder()
                .region(resolveRegion())
                .credentialsProvider(resolveCredentialsProvider());

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            presignerBuilder.endpointOverride(URI.create(endpointUrl));
            presignerBuilder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        }

        return presignerBuilder.build();
    }

    private Region resolveRegion() {
        return Region.of(region == null || region.isBlank() ? "eu-central-1" : region);
    }

    private AwsCredentialsProvider resolveCredentialsProvider() {
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }
}
