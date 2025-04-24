package uk.ac.ebi.protvar.converter;

import java.util.*;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.model.score.*;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.builder.AnnotationsBuilder;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.RNACodon;
import uk.ac.ebi.uniprot.domain.variation.Variant;

@Service
@AllArgsConstructor
public class IsoformConverter {
	private static final Map<String, String> complimentMap = Map.of(
			"A", "T",
			"T", "A",
			"G", "C",
			"C", "G"
	);
	private AnnotationsBuilder annotationsBuilder;

	public List<Isoform> createIsoforms(List<GenomeToProteinMapping> mappingList, String variantAllele,
										Map<String, List<Score>>  scoreMap,
										Map<String, List<Variant>> variantMap,
										Map<String, List<Pocket>> pocketsMap,
										Map<String, List<Interaction>> interactionsMap,
										Map<String, List<Foldx>> foldxMap,
										InputParams params) {
		String canonicalAccession = mappingList.stream().filter(GenomeToProteinMapping::isCanonical)
				.map(GenomeToProteinMapping::getAccession).findFirst().orElse(null);

		return mappingList.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getAccession))
				.entrySet().stream()
				.map(entry -> createIsoform(variantAllele, canonicalAccession, entry.getKey(), entry.getValue(),
						scoreMap, variantMap, pocketsMap, interactionsMap, foldxMap,
						params))
				.sorted().collect(Collectors.toList());
	}

	private Isoform createIsoform(String variantAllele, String canonicalAccession, String accession,
								  List<GenomeToProteinMapping> g2pAccessionMapping,
								  Map<String, List<Score>>  scoreMap,
								  Map<String, List<Variant>> variantMap,
								  Map<String, List<Pocket>> pocketsMap,
								  Map<String, List<Interaction>> interactionsMap,
								  Map<String, List<Foldx>> foldxMap,
								  InputParams params) {
		GenomeToProteinMapping mapping = g2pAccessionMapping.get(0);

		if (mapping.isReverseStrand()) {
			variantAllele = complimentMap.get(variantAllele);
		}

		String codon = mapping.getCodon();
		String variantCodon = replaceChar(codon, variantAllele.charAt(0), mapping.getCodonPosition());

		AminoAcid refAA = AminoAcid.fromOneLetter(mapping.getAa());
		AminoAcid variantAA = RNACodon.valueOf(variantCodon.toUpperCase()).getAa();
		String consequences = AminoAcid.getConsequence(refAA, variantAA);
		List<Transcript> transcripts = extractTranscripts(g2pAccessionMapping);

		Isoform.IsoformBuilder builder = Isoform.builder()
				.accession(accession)
				.refCodon(codon)
				.codonPosition(mapping.getCodonPosition())
				.refAA(refAA.getThreeLetters())
				.variantAA(variantAA.getThreeLetters())
				.variantCodon(variantCodon)
				.canonicalAccession(canonicalAccession)
				.canonical(accession.equals(canonicalAccession))
				.isoformPosition(mapping.getIsoformPosition())
				.transcripts(transcripts)
				.consequences(consequences)
				.proteinName(mapping.getProteinName());

		if (accession.equals(canonicalAccession)) {
			addScores(builder, accession, mapping.getIsoformPosition(), variantAA, scoreMap, params);
			annotationsBuilder.build(accession, mapping.getGenomeLocation(), variantAA.getOneLetter(),
					mapping.getIsoformPosition(), variantMap, pocketsMap, interactionsMap, foldxMap, params, builder);
		}
		return builder.build();
	}

	private void addScores(Isoform.IsoformBuilder builder, String accession, int position, AminoAcid variantAA,
						   Map<String, List<Score>> scoreMap, InputParams params) {
		// Always add AM scores
		String keyAm = Commons.joinWithDash("AM", accession, position, variantAA.getOneLetter());

		scoreMap.getOrDefault(keyAm, Collections.emptyList()).stream().findFirst()
				.map(s -> ((AMScore) s).copy()).ifPresent(builder::amScore);

		// Other scores only if isFun true
		if (params.isFun()) {
			String keyConserv = Commons.joinWithDash("CONSERV", accession, position);
			String keyEve = Commons.joinWithDash("EVE", accession, position, variantAA.getOneLetter());
			String keyEsm = Commons.joinWithDash("ESM", accession, position, variantAA.getOneLetter());

			scoreMap.getOrDefault(keyConserv, Collections.emptyList()).stream().findFirst()
					.map(s -> ((ConservScore) s).copy()).ifPresent(builder::conservScore);

			scoreMap.getOrDefault(keyEve, Collections.emptyList()).stream().findFirst()
					.map(s -> ((EVEScore) s).copy()).ifPresent(builder::eveScore);

			scoreMap.getOrDefault(keyEsm, Collections.emptyList()).stream().findFirst()
					.map(s -> ((ESMScore) s).copy()).ifPresent(builder::esmScore);
		}
	}

	private String replaceChar(String str, char ch, int index) {
		if (ch == 'T') ch = 'U';
		StringBuilder sb = new StringBuilder(str);
		sb.setCharAt(index - 1, ch);
		return sb.toString();
	}

	private List<Transcript> extractTranscripts(List<GenomeToProteinMapping> mappings) {
		return mappings.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getEnst))
				.entrySet().stream()
				.map(e -> Transcript.builder()
						.enst(e.getKey())
						.ensp(e.getValue().get(0).getEnsp())
						.ense(e.getValue().get(0).getEnse())
						.build())
				.collect(Collectors.toList());
	}
}
