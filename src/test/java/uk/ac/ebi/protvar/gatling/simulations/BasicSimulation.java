package uk.ac.ebi.protvar.gatling.simulations;

import io.gatling.core.javaapi.*;
import io.gatling.http.javaapi.*;
import uk.ac.ebi.protvar.gatling.helpers.UserBehaviour;

import java.util.Optional;

import static io.gatling.core.javaapi.Predef.*;
import static io.gatling.http.javaapi.Predef.*;

public class BasicSimulation extends Simulation {

  private final int userCount = Integer.parseInt(getProperty("USERS", "5"));
  private final int rampDuration = Integer.parseInt(getProperty("RAMP_DURATION", "10"));
  private final int testDuration = Integer.parseInt(getProperty("DURATION", "60"));

  public void before() {
    System.out.println("Running test with " + userCount + " users");
    System.out.println("Ramping users over " + rampDuration + " seconds");
    System.out.println("Total test duration: " + testDuration + " seconds");
  }

  public void after() {
    System.out.println("Stress testing complete");
  }

  public BasicSimulation() {
    // be dev hx-rke-wp-webadmin-02-master-1.caas.ebi.ac.uk:31008
    // fe prod hh-rke-wp-webadmin-02-master-1.caas.ebi.ac.uk:32046
    String domain = getProperty("domain", "localhost:8080");
//    HttpProtocolBuilder httpProtocol = http().baseUrl("https://" + domain);
    HttpProtocolBuilder httpProtocol = http().baseUrl("http://" + domain);

        setUp(
      UserBehaviour.regular(testDuration).injectOpen(rampUsers(userCount).during(rampDuration)).protocols(httpProtocol)//,
//      UserBehaviour.highPurchase(testDuration).injectOpen(rampUsers(5).during(10)).protocols(httpProtocol)
    );
//    setUp(scenario("Debug simple").exec(UserJourneys.browseStaticPages()).injectOpen(atOnceUsers(40)).protocols(httpProtocol));
  }

  private String getProperty(String propertyName, String defaultValue) {
    return Optional.ofNullable(System.getenv(propertyName))
      .or(() -> Optional.ofNullable(System.getProperty(propertyName)))
      .orElse(defaultValue);
  }
}
