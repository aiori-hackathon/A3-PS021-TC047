package com.adapteraspring.b;

import com.adapteraspring.config.BProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class BRestClient implements BTransport {
    private final RestClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public BRestClient(BProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.client = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    @Override
    public BResponse send(BRequest request, String faultHeader) throws BTransientException, BRejectedException {
        try {
            var response = client.post().uri("/v1/requests").body(request)
                    .headers(headers -> { if (faultHeader != null) headers.set("X-Fault", faultHeader); })
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        String body = res.getBody() == null ? "" : new String(res.getBody().readAllBytes());
                        return new RawResponse(status, body);
                    });
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                throw new BRejectedException(response.statusCode(), excerpt(response.body()));
            }
            if (response.statusCode() >= 500) {
                throw new BTransientException("B returned HTTP " + response.statusCode());
            }
            if (response.body().isBlank()) {
                throw new BTransientException("B returned an empty success response");
            }
            JsonNode json = mapper.readTree(response.body());
            if (!json.isObject() || !json.hasNonNull("status")) {
                throw new BTransientException("B returned an unparseable response");
            }
            return new BResponse(json.path("dialog_id").textValue(), json.path("task_id").textValue(),
                    json.path("request_id").textValue(), json.path("status").textValue(),
                    json.get("result"), json.path("duplicate").asBoolean(false),
                    json.path("message").isNull() ? null : json.path("message").textValue());
        } catch (BRejectedException | BTransientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BTransientException("B transport failure", exception);
        }
    }

    private static String excerpt(String body) {
        return body.length() <= 300 ? body : body.substring(0, 300);
    }

    private record RawResponse(int statusCode, String body) { }
}
