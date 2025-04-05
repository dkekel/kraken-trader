package ch.kekelidze.krakentrader.log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GrafanaLogService {

  @Value("${grafana.log.token}")
  private String token;

  public void log(String message) {
    JSONObject logObject = new JSONObject();
    logObject.put("streams", new JSONArray()
        .put(new JSONObject()
            .put("stream", new JSONObject()
                .put("Trading", "XRP"))
            .put("values", new JSONArray()
                .put(new JSONArray()
                    .put(String.valueOf(Instant.now().toEpochMilli() * 1000000))
                    .put(message)))));

    String logs = logObject.toString();

    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://logs-prod-012.grafana.net/loki/api/v1/push"))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + token)
          .POST(HttpRequest.BodyPublishers.ofString(logs, StandardCharsets.UTF_8))
          .build();

      client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      log.error("Failed to log message: {}", message, e);
    }
  }
}
