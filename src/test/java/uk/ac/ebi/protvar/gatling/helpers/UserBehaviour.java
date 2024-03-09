package uk.ac.ebi.protvar.gatling.helpers;

import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.ScenarioBuilder;
import static io.gatling.javaapi.core.CoreDsl.*;

public class UserBehaviour {
  public static ScenarioBuilder regular(int testDuration) {
    return scenario("Regular Load Test")
      .during(testDuration)
      .on(
        randomSwitch().on(
                new Choice.WithWeight(10d, exec(UserJourneys.browseStaticPages())),
      new Choice.WithWeight(80d, exec(UserJourneys.searchMappingsAndBrowseAnnotations()))
//          new Possibility.WithWeight(10d, exec(UserJourneys.))
        ));
  }

  public static ScenarioBuilder highPurchase(int testDuration) {
    return scenario("High Load Test")
      .during(testDuration)
      .on(
        randomSwitch().on(
          new Choice.WithWeight(25d, exec(UserJourneys.browseStaticPages()))//,
//          new Possibility.WithWeight(25d, exec(UserJourneys.)),
//          new Possibility.WithWeight(50d, exec(UserJourneys.))
        )
      );
  }
}
