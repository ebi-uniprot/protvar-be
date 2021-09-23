package uk.ac.ebi.pepvep.fetcher.csv;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import uk.ac.ebi.pepvep.utils.CSVUtils;
import uk.ac.ebi.pepvep.model.response.PDBeStructure;

@Service
public class CSVStructureDataFetcher {

	public String fetch(List<PDBeStructure> proteinStructure) {
		StringJoiner structure = new StringJoiner("|");
		Map<String, List<PDBeStructure>> pdbAccessionMap = proteinStructure.stream()
				.collect(Collectors.groupingBy(PDBeStructure::getPdb_id));

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
		return CSVUtils.getValOrNA(structure.toString());
	}

	private String buildChain(List<PDBeStructure> structures) {
		StringJoiner structure = new StringJoiner(",");
		if (structures != null && !structures.isEmpty())
			structures.forEach(str -> structure.add(str.getChain_id() + str.getStart()));
		return structure.toString();
	}

}
