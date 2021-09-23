package uk.ac.ebi.pepvep.converter;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.pepvep.utils.AminoAcidsThreeLetter;
import uk.ac.ebi.pepvep.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.pepvep.builder.OptionalAttributesBuilder;
import uk.ac.ebi.pepvep.model.response.Ensp;
import uk.ac.ebi.pepvep.model.response.GenomeToProteinMapping;
import uk.ac.ebi.pepvep.model.response.IsoFormMapping;
import uk.ac.ebi.pepvep.model.response.Transcript;
import uk.ac.ebi.pepvep.utils.Codon2AminoAcid;

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
			String variantAllele, List<OPTIONS> options) {
		String canonicalAccession = mappingList.stream().filter(GenomeToProteinMapping::isCanonical)
			.map(GenomeToProteinMapping::getAccession).findFirst().orElse(null);

		Map<String, List<GenomeToProteinMapping>> accessionMapping = mappingList.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getAccession));

		return accessionMapping.keySet().stream()
			.map(accession -> createIsoform(refAlleleUser, variantAllele, canonicalAccession, accession, accessionMapping.get(accession), options))
			.sorted().collect(Collectors.toList());

	}

	private IsoFormMapping createIsoform(String refAlleleUser, String variantAllele, String canonicalAccession,
			String accession, List<GenomeToProteinMapping> g2pAccessionMapping, List<OPTIONS> options) {
		GenomeToProteinMapping genomeToProteinMapping = g2pAccessionMapping.get(0);

		boolean strand = genomeToProteinMapping.isReverseStrand();
		if (strand) {
			variantAllele = complimentMap.get(variantAllele);
		}
		long genomicLocation = genomeToProteinMapping.getGenomeLocation();
		String codon = genomeToProteinMapping.getCodon();
		String userCodon = replaceChar(codon, refAlleleUser.charAt(0), genomeToProteinMapping.getCodonPosition());
		String variantCodon = replaceChar(codon, variantAllele.charAt(0), genomeToProteinMapping.getCodonPosition());
		List<Ensp> ensps = mergeMappings(g2pAccessionMapping);
		String variantAA3Letter = Codon2AminoAcid.getAminoAcid(variantCodon.toUpperCase());
		String consequences = getConsequence(AminoAcidsThreeLetter.getThreeLetterFromSingleLetter(genomeToProteinMapping.getAa()),
				Codon2AminoAcid.getAminoAcid(variantCodon.toUpperCase()));

		IsoFormMapping.IsoFormMappingBuilder builder = IsoFormMapping.builder().accession(accession).refCodon(codon)
				.userCodon(userCodon).cdsPosition(genomeToProteinMapping.getCodonPosition())
				.refAA(AminoAcidsThreeLetter.getThreeLetterFromSingleLetter(genomeToProteinMapping.getAa()))
				.userAA(variantAA3Letter)
				.variantAA(Codon2AminoAcid.getAminoAcid(variantCodon.toUpperCase())).variantCodon(variantCodon)
				.canonicalAccession(canonicalAccession).canonical(isCanonical(accession, canonicalAccession))
				.isoformPosition(genomeToProteinMapping.getIsoformPosition()).translatedSequences(ensps)
				.consequences(consequences).proteinName(genomeToProteinMapping.getProteinName());
		if (isCanonical(accession, canonicalAccession))
			optionalAttributeBuilder.build(accession, genomicLocation, genomeToProteinMapping.getIsoformPosition(), options, builder);
		return builder.build();
	}

	private String getConsequence(String codon, String variantCodon) {
		if (Objects.equals(codon, variantCodon))
			return "synonymous";
		if ("*".equals(variantCodon))
			return "stop gained";
		return "missense";
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
