package uk.ac.ebi.protvar.gatling.helpers;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static io.gatling.javaapi.core.CoreDsl.*;

import uk.ac.ebi.protvar.utils.TestHelper;

import java.util.List;

public interface Api {
  String SUB_DOMAIN = "/";
  FeederBuilder.Batchable<String> accession = csv("gatling/accessionPosition.csv").random();

  ChainBuilder mapping = exec(
    http("Mapping")
      .post(SUB_DOMAIN + "mapping")
      .header("content-type", "application/json")
      .body(StringBody(TestHelper.getRandomMappingsJson(25)))
      .check(status().is(200))
      .check(jmesPath("mappings").ofList().transform(List::size).is(25))
      .check(jmesPath("invalidInputs").notNull())
  );

  ChainBuilder functionalAnnotations = feed(accession)
    .exec(
      http("Functional annotations")
        .get(SUB_DOMAIN + "function/${accession}/${proteinPosition}")
        .check(status().is(200))
        .check(jmesPath("position").is(session -> session.getString("proteinPosition")))
        .check(jmesPath("accession").is(session -> session.getString("accession")))
    );

  ChainBuilder populationAnnotations = feed(accession)
    .exec(http("Population annotations")
      .get(SUB_DOMAIN + "population/${accession}/${proteinPosition}")
      .check(status().is(200))
      .check(jmesPath("proteinColocatedVariant").notNull())
    );
}
