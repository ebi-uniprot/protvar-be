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

		return mappingList.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getAccession))
				.entrySet().stream()
				.map(entry -> createIsoform(altBase, canonicalAccession, entry.getKey(), entry.getValue(),
						scoreMap))
				.filter(Objects::nonNull)
				.sorted().collect(Collectors.toList());
	}

	private Isoform createIsoform(String altBase, String canonicalAccession, String accession,
								  List<GenomeToProteinMapping> g2pAccessionMapping,
								  Map<String, List<Score>>  scoreMap) {
		GenomeToProteinMapping mapping = g2pAccessionMapping.get(0);
		// Guard against incomplete g2p_mapping rows. A null aa or codon would
		// NPE on AminoAcid.fromOneLetter / altCodon.toUpperCase below.
		// (codonPosition is primitive int and silently becomes 0 for SQL NULL,
		// which downstream code tolerates — no NPE risk there.)
		if (mapping.getAa() == null || mapping.getCodon() == null) {
			LOGGER.warn("Skipping isoform for {}: incomplete mapping (aa={}, codon={})",
					accession, mapping.getAa(), mapping.getCodon());
			return null;
		}
		AminoAcid refAA = AminoAcid.fromOneLetter(mapping.getAa());
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

			// Annotation URIs
			builder.referenceFunctionUri("/function/" + accession + "/" + mapping.getIsoformPosition()
					+ (altAA.getOneLetter() != null && !altAA.getOneLetter().isEmpty()
					? "?variantAA=" + altAA : ""));

			String genomicVariant = String.format("%s-%d-%s-%s",
					mapping.getChromosome(), mapping.getGenomeLocation(),
					mapping.getBaseNucleotide(), altBase);

			builder.populationObservationsUri("/population/" + accession + "/" + mapping.getIsoformPosition()
					+ "?genomicVariant=" + genomicVariant);

			builder.proteinStructureUri("/structure/" + accession + "/" + mapping.getIsoformPosition());
		}
		return builder.build();
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
