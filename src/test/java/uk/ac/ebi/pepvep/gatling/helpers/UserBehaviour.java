package uk.ac.ebi.pepvep.gatling.helpers;

import io.gatling.core.javaapi.Possibility;
import io.gatling.core.javaapi.ScenarioBuilder;

import static io.gatling.core.javaapi.Predef.*;

public class UserBehaviour {
  public static ScenarioBuilder regular(int testDuration) {
    return scenario("Regular Load Test")
      .during(testDuration)
      .loop(
        randomSwitch(
          new Possibility.WithWeight(75d, exec(UserJourneys.browseStaticPages()))//,
//          new Possibility.WithWeight(15d, exec(UserJourneys.)),
//          new Possibility.WithWeight(10d, exec(UserJourneys.))
        )
      );
  }

  public static ScenarioBuilder highPurchase(int testDuration) {
    return scenario("High Load Test")
      .during(testDuration)
      .loop(
        randomSwitch(
          new Possibility.WithWeight(25d, exec(UserJourneys.browseStaticPages()))//,
//          new Possibility.WithWeight(25d, exec(UserJourneys.)),
//          new Possibility.WithWeight(50d, exec(UserJourneys.))
        )
      );
  }
}
