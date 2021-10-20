package uk.ac.ebi.pepvep.gatling;

import io.gatling.recorder.GatlingRecorder;
import io.gatling.recorder.config.RecorderPropertiesBuilder;
import scala.Option;

import java.nio.file.Path;

public class Recorder {
  public static void main(String[] args) {
    RecorderPropertiesBuilder props = new RecorderPropertiesBuilder()
      .simulationsFolder(IDEPathHelper.mavenSourcesDirectory.toString())
      .resourcesFolder(IDEPathHelper.mavenResourcesDirectory.toString())
      .simulationPackage("uk.ac.ebi.pepvep.gatling.simulations");

    GatlingRecorder.fromMap(props.build(), Option.<Path> apply(IDEPathHelper.recorderConfigFile));
  }
}
