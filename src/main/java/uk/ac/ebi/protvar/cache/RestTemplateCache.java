package uk.ac.ebi.protvar.cache;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class RestTemplateCache extends RestTemplate {
  private final int SIZE = 3;
  private static final Logger logger = LoggerFactory.getLogger(RestTemplateCache.class);
  private final List<Data> cache = new ArrayList<>(SIZE);

  @Nonnull
  @Override
  public <T> ResponseEntity<T> getForEntity(@Nonnull URI url, @Nonnull Class<T> responseType) throws RestClientException {
    var response = getFromCache(url);
    if (Objects.nonNull(response)) {
      logger.trace("Using Cache to Response back for {}", url);
      return (ResponseEntity<T>) response;
    }

    var apiResponse = super.getForEntity(url, responseType);
    updateCache(url, apiResponse);
    return apiResponse;
  }

  private void updateCache(@Nonnull URI url, @Nonnull ResponseEntity<?> response) {
    if (cache.size() >= SIZE) {
      cache.remove(0);
    }
    cache.add(new Data(url, response));
  }

  @AllArgsConstructor
  private static class Data {
    private URI key;
    private ResponseEntity<?> value;
  }

  private ResponseEntity<?> getFromCache(URI key) {
    for (int i = cache.size() - 1; i >= 0; i--) {
      var ele = cache.get(i);
      if (ele.key.equals(key))
        return ele.value;
    }
    return null;
  }
}
