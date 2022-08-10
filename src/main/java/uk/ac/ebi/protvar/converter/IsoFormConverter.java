package uk.ac.ebi.protvar.converter;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.builder.OptionalAttributesBuilder;
import uk.ac.ebi.protvar.utils.RNACodon;

@Service
@AllArgsConstructor
public class IsoFormConverter {

	private static final String BASE_A = "A";
	private static final String BASE_T = "T";
	private static final String BASE_G = "G";
	private static final String BASE_C = "C";
	private static final char BASE_U = 'U';

	protected static Map<String, String> complimentMap;
	private OptionalAttributesBuilder optionalAttributeBuilder;

	@PostConstruct
	private void init() {
		initComplimentMap();
	}

	public List<IsoFormMapping> createIsoforms(List<GenomeToProteinMapping> mappingList, String refAlleleUser,
											   String variantAllele, Map<String, List<EVEScore>> eveScoreMap, List<OPTIONS> options) {
		String canonicalAccession = mappingList.stream().filter(GenomeToProteinMapping::isCanonical)
				.map(GenomeToProteinMapping::getAccession).findFirst().orElse(null);

		Map<String, List<GenomeToProteinMapping>> accessionMapping = mappingList.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getAccession));

		return accessionMapping.keySet().stream()
				.map(accession -> createIsoform(refAlleleUser, variantAllele, canonicalAccession, accession, accessionMapping.get(accession), eveScoreMap, options))
				.sorted().collect(Collectors.toList());

	}

	private IsoFormMapping createIsoform(String refAlleleUser, String variantAllele, String canonicalAccession,
			String accession, List<GenomeToProteinMapping> g2pAccessionMapping, Map<String, List<EVEScore>> eveScoreMap,
										 List<OPTIONS> options) {
		GenomeToProteinMapping genomeToProteinMapping = g2pAccessionMapping.get(0);

		boolean strand = genomeToProteinMapping.isReverseStrand();
		if (strand) {
			variantAllele = complimentMap.get(variantAllele);
		}
		long genomicLocation = genomeToProteinMapping.getGenomeLocation();
		String codon = genomeToProteinMapping.getCodon();
		//String userCodon = replaceChar(codon, refAlleleUser.charAt(0), genomeToProteinMapping.getCodonPosition());
		String variantCodon = replaceChar(codon, variantAllele.charAt(0), genomeToProteinMapping.getCodonPosition());
		List<Ensp> ensps = mergeMappings(g2pAccessionMapping);
		AminoAcid refAA = AminoAcid.fromOneLetter(genomeToProteinMapping.getAa());
		AminoAcid variantAA = RNACodon.valueOf(variantCodon.toUpperCase()).getAa();

		EVEScore eveScore = calcEveScore(genomeToProteinMapping, eveScoreMap, variantAA);

		String consequences = AminoAcid.getConsequence(refAA, variantAA);

		IsoFormMapping.IsoFormMappingBuilder builder = IsoFormMapping.builder()
				.accession(accession).refCodon(codon)
				//TODO clean up
				//.userCodon(userCodon)
				.cdsPosition(genomeToProteinMapping.getCodonPosition())
				.refAA(refAA.getThreeLetters())
				//.userAA(variantAA.getThreeLetters())
				.variantAA(variantAA.getThreeLetters()).variantCodon(variantCodon)
				.canonicalAccession(canonicalAccession).canonical(isCanonical(accession, canonicalAccession))
				.isoformPosition(genomeToProteinMapping.getIsoformPosition()).translatedSequences(ensps)
				.consequences(consequences).proteinName(genomeToProteinMapping.getProteinName());
		if (eveScore != null) {
			builder.eveScore(eveScore.getScore());
			builder.eveClass(eveScore.getEveClass().getNum());
		}
		if (isCanonical(accession, canonicalAccession))
			optionalAttributeBuilder.build(accession, genomicLocation, genomeToProteinMapping.getIsoformPosition(), options, builder);
		return builder.build();
	}

	private EVEScore calcEveScore(GenomeToProteinMapping genomeToProteinMapping, Map<String, List<EVEScore>> eveScoreMap, AminoAcid variantAA) {
		EVEScore eveScore = null;

		if (genomeToProteinMapping.getAccession() != null && genomeToProteinMapping.getAa() != null
				&& genomeToProteinMapping.isCanonical()) {

			String eveScoreKey = genomeToProteinMapping.getAccession() + "-" + genomeToProteinMapping.getIsoformPosition() + "-" +
					genomeToProteinMapping.getAa();
			if (eveScoreMap.containsKey(eveScoreKey)) {
				List<EVEScore> eveScores = eveScoreMap.get(eveScoreKey).stream()
						.filter(e -> e.getMtAA().equals(variantAA.getOneLetter()))
						.collect(Collectors.toList());

				if (eveScores.size() == 1) {
					eveScore = eveScores.get(0);
				}
			}
		}
		return eveScore;
	}

	private String replaceChar(String str, char ch, int index) {
		StringBuilder codon = new StringBuilder(str);
		if (BASE_T.equals(Character.toString(ch)))
			ch = BASE_U;
		codon.setCharAt(index - 1, ch);
		return codon.toString();
	}

	private List<Ensp> mergeMappings(List<GenomeToProteinMapping> g2pAccessionMapping) {
		Map<String, List<GenomeToProteinMapping>> enspMapping = g2pAccessionMapping.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getEnsp));

		return enspMapping.keySet().stream().map(enspId -> {
			List<Transcript> transcripts = findTranscripts(enspMapping.get(enspId));
			return Ensp.builder().ensp(enspId).transcripts(transcripts).build();
		}).collect(Collectors.toList());

	}

	private boolean isCanonical(String accession, String canonicalAccession) {
		return Objects.equals(accession, canonicalAccession);
	}

	private List<Transcript> findTranscripts(List<GenomeToProteinMapping> g2pEnspMappings) {
		Map<String, List<GenomeToProteinMapping>> enstMapping = g2pEnspMappings.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getEnst));

		return enstMapping.keySet().stream().map(enst -> {
			List<GenomeToProteinMapping> g2pEnstMappings = enstMapping.get(enst);
			return Transcript.builder().enst(enst).ense(g2pEnstMappings.get(0).getEnse()).build();
		}).collect(Collectors.toList());

	}

	private void initComplimentMap() {
		complimentMap = new HashMap<>();
		complimentMap.put(BASE_A, BASE_T);
		complimentMap.put(BASE_T, BASE_A);
		complimentMap.put(BASE_G, BASE_C);
		complimentMap.put(BASE_C, BASE_G);

	}
}
