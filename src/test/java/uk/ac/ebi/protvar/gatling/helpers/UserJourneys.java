package uk.ac.ebi.protvar.gatling.helpers;

import java.time.Duration;

import io.gatling.core.javaapi.*;

import static io.gatling.core.javaapi.Predef.*;

public class UserJourneys {
  private final static Duration minPause = Duration.ofMillis(100);
  private final static Duration maxPause = Duration.ofMillis(500);

  public static ChainBuilder browseStaticPages() {
    return exec(Pages.home)
      .pause(maxPause)
      .exec(Pages.aboutUs)
      .pause(minPause, maxPause)
      .exec(Pages.contactUs);
  }

  public static ChainBuilder searchMappingsAndBrowseAnnotations() {
    return exec(Api.mapping)
      .pause(minPause, maxPause)
      .repeat(5)
      .loop(
        exec(Api.functionalAnnotations)
          .pause(minPause, maxPause)
          .exec(Api.populationAnnotations)
      );
  }
}
