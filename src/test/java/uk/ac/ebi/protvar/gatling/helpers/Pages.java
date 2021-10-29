package uk.ac.ebi.protvar.gatling.helpers;

import io.gatling.core.javaapi.ChainBuilder;

import static io.gatling.core.javaapi.Predef.*;
import static io.gatling.http.javaapi.Predef.*;

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
