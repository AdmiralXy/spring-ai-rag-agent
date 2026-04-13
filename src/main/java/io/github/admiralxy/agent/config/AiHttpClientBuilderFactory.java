package io.github.admiralxy.agent.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Component
public class AiHttpClientBuilderFactory {

    private static final TrustManager[] TRUST_ALL_MANAGERS = new TrustManager[]{new TrustAllX509TrustManager()};

    private final boolean insecureSsl;

    public AiHttpClientBuilderFactory(@Value("${app.ai.insecure-ssl:false}") boolean insecureSsl) {
        this.insecureSsl = insecureSsl;
    }

    public RestClient.Builder createRestClientBuilder() {
        if (!insecureSsl) {
            return RestClient.builder();
        }

        SSLContext sslContext = createInsecureSslContext();
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm(null);

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .build();

        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(client));
    }

    public WebClient.Builder createWebClientBuilder() {
        if (!insecureSsl) {
            return WebClient.builder();
        }

        HttpClient httpClient = HttpClient.create()
                .secure(sslSpec -> sslSpec
                        .sslContext(createInsecureNettySslContext())
                        .handlerConfigurator(sslHandler -> {
                            SSLParameters sslParameters = sslHandler.engine().getSSLParameters();
                            sslParameters.setEndpointIdentificationAlgorithm(null);
                            sslHandler.engine().setSSLParameters(sslParameters);
                        }));

        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    private static SSLContext createInsecureSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, TRUST_ALL_MANAGERS, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to configure insecure SSL context", ex);
        }
    }

    private static io.netty.handler.ssl.SslContext createInsecureNettySslContext() {
        try {
            return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } catch (SSLException ex) {
            throw new IllegalStateException("Unable to configure insecure Netty SSL context", ex);
        }
    }

    private static final class TrustAllX509TrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
