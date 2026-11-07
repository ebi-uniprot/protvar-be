package uk.ac.ebi.protvar.converter;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Isoform;
import uk.ac.ebi.protvar.model.response.Transcript;
import uk.ac.ebi.protvar.model.score.AmScore;
import uk.ac.ebi.protvar.model.score.PopEveScore;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.model.score.ScoreType;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.protvar.types.Codon;
import uk.ac.ebi.protvar.utils.VariantKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class IsoformConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(IsoformConverter.class);

	public List<Isoform> createIsoforms(String altBase,
			List<GenomeToProteinMapping> mappingList,
			Map<String, List<Score>>  scoreMap) {
		String canonicalAccession = mappingList.stream().filter(GenomeToProteinMapping::isCanonical)
				.map(GenomeToProteinMapping::getAccession).findFirst().orElse(null);

		return withoutCanonicalCopies(mappingList.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getAccession))
				.entrySet().stream()
				.map(entry -> createIsoform(altBase, canonicalAccession, entry.getKey(), entry.getValue(),
						scoreMap))
				.filter(Objects::nonNull)
				.sorted().collect(Collectors.toList()));
	}

	/**
	 * Drops a non-canonical isoform with the canonical's position, change and transcripts: the same protein listed
	 * twice, usually X-1 next to X. Compared, not matched by name: X-1 is not always the canonical sequence.
	 * The canonical keeps their accessions in sameAs.
	 */
	static List<Isoform> withoutCanonicalCopies(List<Isoform> isoforms) {
		Isoform canonical = isoforms.stream().filter(Isoform::isCanonical).findFirst().orElse(null);
		if (canonical == null) return isoforms;
		Map<Boolean, List<Isoform>> copy = isoforms.stream()
				.collect(Collectors.partitioningBy(i -> !i.isCanonical() && sameMapping(i, canonical)));
		canonical.setSameAs(copy.get(true).stream().map(Isoform::getAccession).toList());
		return copy.get(false);
	}

	private static boolean sameMapping(Isoform a, Isoform b) {
		return a.getIsoformPosition() == b.getIsoformPosition()
				&& a.getAminoAcidChange().equals(b.getAminoAcidChange())
				&& transcriptIds(a).equals(transcriptIds(b));
	}

	private static Set<String> transcriptIds(Isoform isoform) {
		return isoform.getTranscripts() == null ? Set.of()
				: isoform.getTranscripts().stream().map(Transcript::getEnst).collect(Collectors.toSet());
	}

	private Isoform createIsoform(String altBase, String canonicalAccession, String accession,
								  List<GenomeToProteinMapping> g2pAccessionMapping,
								  Map<String, List<Score>>  scoreMap) {
		// a transcript row with a codon when the accession has one (rows come codon-first, see MappingRepo)
		GenomeToProteinMapping mapping = g2pAccessionMapping.get(0);
		if (mapping.getAa() == null) {
			LOGGER.warn("Skipping isoform for {}: incomplete mapping (aa=null)", accession);
			return null;
		}
		AminoAcid refAA = AminoAcid.fromOneLetter(mapping.getAa());
		if (mapping.getCodon() == null) {
			// a real mapping without a readable codon: the position and residue, change unknown (PV-113)
			Isoform.IsoformBuilder builder = Isoform.builder()
					.accession(accession)
					.codonPosition(mapping.getCodonPosition())
					.refAA(refAA.getThreeLetter())
					.canonicalAccession(canonicalAccession)
					.canonical(accession.equals(canonicalAccession))
					.isoformPosition(mapping.getIsoformPosition())
					.transcripts(extractTranscripts(g2pAccessionMapping))
					.proteinName(mapping.getProteinName());
			if (accession.equals(canonicalAccession)) addAnnotationUris(builder, accession, mapping, altBase, null);
			return builder.build();
		}
		String altCodon = mapping.getAltCodon(altBase);
		AminoAcid altAA = Codon.valueOf(altCodon.toUpperCase()).getAa();
		String consequences = AminoAcid.getConsequence(refAA, altAA);
		List<Transcript> transcripts = extractTranscripts(g2pAccessionMapping);

		Isoform.IsoformBuilder builder = Isoform.builder()
				.accession(accession)
				.refCodon(mapping.getCodon())
				.codonPosition(mapping.getCodonPosition())
				.refAA(refAA.getThreeLetter())
				.variantAA(altAA.getThreeLetter())
				.variantCodon(altCodon)
				.canonicalAccession(canonicalAccession)
				.canonical(accession.equals(canonicalAccession))
				.isoformPosition(mapping.getIsoformPosition())
				.transcripts(transcripts)
				.consequences(consequences)
				.proteinName(mapping.getProteinName());

		if (accession.equals(canonicalAccession)) {
			// Add AlphaMissense score
			String amScoreKey = VariantKey.protein(ScoreType.AM, accession, mapping.getIsoformPosition(), altAA.getOneLetter());
			scoreMap.getOrDefault(amScoreKey, Collections.emptyList()).stream().findFirst()
					.map(s -> ((AmScore) s).copySubclassFields()).ifPresent(builder::amScore);

			// Add popEVE score
			String popEveKey = VariantKey.protein(ScoreType.POPEVE, accession, mapping.getIsoformPosition(), altAA.getOneLetter());
			scoreMap.getOrDefault(popEveKey, Collections.emptyList()).stream().findFirst()
					.map(s -> ((PopEveScore) s).copySubclassFields()).ifPresent(builder::popEveScore);

			addAnnotationUris(builder, accession, mapping, altBase, altAA);
		}
		return builder.build();
	}

	// function / population / structure links of the canonical isoform; altAA null when the change is unknown
	private static void addAnnotationUris(Isoform.IsoformBuilder builder, String accession,
										  GenomeToProteinMapping mapping, String altBase, AminoAcid altAA) {
		builder.referenceFunctionUri("/function/" + accession + "/" + mapping.getIsoformPosition());
		builder.referencePredictionUri("/prediction/" + accession + "/" + mapping.getIsoformPosition()
				+ (altAA != null && altAA.getOneLetter() != null && !altAA.getOneLetter().isEmpty()
				? "?variantAA=" + altAA.getOneLetter() : ""));

		String genomicVariant = String.format("%s-%d-%s-%s",
				mapping.getChromosome(), mapping.getGenomeLocation(),
				mapping.getBaseNucleotide(), altBase);

		builder.populationObservationsUri("/population/" + accession + "/" + mapping.getIsoformPosition()
				+ "?genomicVariant=" + genomicVariant);

		builder.proteinStructureUri("/structure/" + accession + "/" + mapping.getIsoformPosition());
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
