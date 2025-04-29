package uk.ac.ebi.protvar.fetcher.csv;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import uk.ac.ebi.pdbe.model.PDBeStructureResidue;
import uk.ac.ebi.protvar.utils.CsvUtils;

@Service
public class CsvStructureDataFetcher {

	public String fetch(List<PDBeStructureResidue> proteinStructure) {
		StringJoiner structure = new StringJoiner("|");
		Map<String, List<PDBeStructureResidue>> pdbAccessionMap = proteinStructure.stream()
				.collect(Collectors.groupingBy(PDBeStructureResidue::getPdb_id));

		pdbAccessionMap.forEach((key, v) -> {
			StringBuilder builder = new StringBuilder();
			builder.append(key);
			builder.append(";");
			builder.append(buildChain(v));
			builder.append(";");
			if (v != null && !v.isEmpty()) {
				builder.append(v.get(0).getResolution());
				builder.append(";");
				builder.append(v.get(0).getExperimental_method());
			}
			structure.add(builder.toString());
		});
		return CsvUtils.getValOrNA(structure.toString());
	}

	private String buildChain(List<PDBeStructureResidue> structures) {
		StringJoiner structure = new StringJoiner(",");
		if (structures != null && !structures.isEmpty())
			structures.forEach(str -> structure.add(str.getChain_id() + str.getStart()));
		return structure.toString();
	}

}
