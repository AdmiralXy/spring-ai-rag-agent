package io.github.admiralxy.agent.config;

import io.github.admiralxy.agent.config.properties.OutboundProperties;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
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

    private static final String TLS = "TLS";
    private static final String SSL_CONTEXT_FAILED = "Unable to configure insecure SSL context";
    private static final String NETTY_SSL_CONTEXT_FAILED = "Unable to configure insecure Netty SSL context";
    private static final X509TrustManager TRUST_ALL_MANAGER = new TrustAllX509TrustManager();
    private static final TrustManager[] TRUST_ALL_MANAGERS = new TrustManager[]{TRUST_ALL_MANAGER};

    private final boolean insecureSsl;

    public AiHttpClientBuilderFactory(OutboundProperties outboundProperties) {
        this.insecureSsl = outboundProperties.isInsecureSsl();
    }

    public boolean isInsecureSslEnabled() {
        return insecureSsl;
    }

    public OpenAiHttpClientBuilderCustomizer openAiHttpClientCustomizer() {
        return builder -> {
            if (insecureSsl) {
                builder.sslSocketFactory(createInsecureSslContext().getSocketFactory())
                        .trustManager(TRUST_ALL_MANAGER)
                        .hostnameVerifier((hostname, session) -> true);
            }
        };
    }

    public AnthropicHttpClientBuilderCustomizer anthropicHttpClientCustomizer() {
        return builder -> {
            if (insecureSsl) {
                builder.sslSocketFactory(createInsecureSslContext().getSocketFactory())
                        .trustManager(TRUST_ALL_MANAGER)
                        .hostnameVerifier((hostname, session) -> true);
            }
        };
    }

    public RestClient.Builder createRestClientBuilder() {
        if (!insecureSsl) {
            return RestClient.builder();
        }
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm(null);
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .sslContext(createInsecureSslContext())
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
            SSLContext sslContext = SSLContext.getInstance(TLS);
            sslContext.init(null, TRUST_ALL_MANAGERS, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(SSL_CONTEXT_FAILED, e);
        }
    }

    private static SslContext createInsecureNettySslContext() {
        try {
            return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } catch (SSLException e) {
            throw new IllegalStateException(NETTY_SSL_CONTEXT_FAILED, e);
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
