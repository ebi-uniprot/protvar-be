package uk.ac.ebi.protvar.cache;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class RestTemplateCache extends RestTemplate {
  //private final int MAX_CACHE_SIZE = 10000;
  private static final Logger logger = LoggerFactory.getLogger(RestTemplateCache.class);

  // RestTemplate level cache (L1) to determine an external API call is needed or not
  // Cached data is response entity
  private final Map<URI, ResponseEntity<?>> cache = new ConcurrentHashMap<>();

  @Nonnull
  @Override
  public <T> ResponseEntity<T> getForEntity(@Nonnull URI url, @Nonnull Class<T> responseType) throws RestClientException {
    var response = getFromCache(url);
    if (Objects.nonNull(response)) {
      logger.info("Using RestTemplate cache for {}", url);
      return (ResponseEntity<T>) response;
    }

    var apiResponse = super.getForEntity(url, responseType);
    cache.put(url, apiResponse);

    return apiResponse;
  }

  private ResponseEntity<?> getFromCache(URI key) {
    if (cache.containsKey(key))
      return cache.get(key);
    return null;
  }
}