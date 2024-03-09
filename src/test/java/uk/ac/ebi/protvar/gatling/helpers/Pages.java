package uk.ac.ebi.protvar.gatling.helpers;

import io.gatling.javaapi.core.ChainBuilder;
import static io.gatling.javaapi.http.HttpDsl.*;
import static io.gatling.javaapi.core.CoreDsl.*;

public interface Pages {
  String SUB_DOMAIN = "/";

  ChainBuilder home = exec(
    http("Home Page")
      .get(SUB_DOMAIN)
      .check(status().is(200))
  );

  ChainBuilder aboutUs = exec(http("About Us Page")
    .get(SUB_DOMAIN + "about")
    .check(status().is(200))
  );

  ChainBuilder contactUs = exec(http("Contact Us Page")
    .get(SUB_DOMAIN + "contact")
    .check(status().is(200))
  );
}
