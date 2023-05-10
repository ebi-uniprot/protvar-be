package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.uniprot.common.model.DSPSource;
import uk.ac.ebi.uniprot.common.model.Evidence;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.google.common.collect.Lists;

public class FetcherUtils {

  public static final int PARTITION_SIZE = 100;
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

  public static String pluralise(int qty) {
    if (qty > 1) {
      return "s";
    }
    return "";
  }
  public static String isOrAre(int qty) {
    if (qty > 1) {
      return "are";
    }
    return "is";
  }

  public static List<Set<String>> partitionSet(Set<String> inputSet, int partitionSize) {
    List<String> inputList = new ArrayList<>(inputSet);
    List<List<String>> partitions = Lists.partition(inputList, partitionSize);
    List<Set<String>> partitionSet =  partitions.stream().map((Function<List<String>, HashSet<String>>) HashSet::new).collect(Collectors.toList());
    return partitionSet;
  }
}
