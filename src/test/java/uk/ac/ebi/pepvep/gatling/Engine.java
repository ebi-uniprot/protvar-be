package uk.ac.ebi.pepvep.gatling;

import io.gatling.app.Gatling;
import io.gatling.core.config.GatlingPropertiesBuilder;

//https://github.com/gatling/gatling-maven-plugin-demo-java
//https://gatling.io/docs/gatling/reference/current/extensions/maven_plugin/
//https://github.com/gatling/gatling-academy-module-2/tree/C9_Refactor
public class Engine {

  public static void main(String[] args) {
    GatlingPropertiesBuilder props = new GatlingPropertiesBuilder()
      .resourcesDirectory(IDEPathHelper.mavenResourcesDirectory.toString())
      .resultsDirectory(IDEPathHelper.resultsDirectory.toString())
      .binariesDirectory(IDEPathHelper.mavenBinariesDirectory.toString());

    Gatling.fromMap(props.build());
  }
}