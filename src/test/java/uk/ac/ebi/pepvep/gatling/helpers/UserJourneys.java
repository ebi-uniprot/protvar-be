package uk.ac.ebi.pepvep.gatling.helpers;

import java.time.Duration;

import io.gatling.core.javaapi.*;
import io.gatling.http.javaapi.*;

import static io.gatling.core.javaapi.Predef.*;
import static io.gatling.http.javaapi.Predef.*;

public class UserJourneys {
  private final static Duration minPause = Duration.ofMillis(100);
  private final static Duration maxPause = Duration.ofMillis(500);

  public static ChainBuilder browseStaticPages() {
    return exec(Pages.home)
      .pause(maxPause)
      .exec(Pages.aboutUs)
      .pause(minPause, maxPause)
      .exec(Pages.contactUs)
      .repeat(5)
      .loop(
        exec(Api.functionalAnnotations)
          .pause(minPause, maxPause)
          .exec(Api.populationAnnotations)
      );
  }

  public static ChainBuilder searchMappingsAndBrowseAnnotations() {
    return exec(Api.mapping)

      ;
  }
}
