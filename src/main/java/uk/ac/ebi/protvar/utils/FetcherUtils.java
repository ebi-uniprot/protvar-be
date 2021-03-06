package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.model.api.DSPSource;
import uk.ac.ebi.protvar.model.api.Evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class FetcherUtils {
  public static String evidencesToString(List<Evidence> evidences) {
    if (evidences != null && !evidences.isEmpty()) {
      Map<String, List<DSPSource>> evidenceSourceMap = evidences.stream()
        .map(Evidence::getSource).filter(Objects::nonNull)
        .collect(Collectors.groupingBy(DSPSource::getName));
      if (evidenceSourceMap != null) {
        StringBuilder builder = new StringBuilder();
        evidenceSourceMap.forEach((k, v) -> {
          builder.append(k).append(":[");
          builder.append(v.stream().map(DSPSource::getId).collect(Collectors.joining(",")));
          builder.append("]");
        });
        return "(" + builder + ")";
      }
    }
    return "";
  }
}
