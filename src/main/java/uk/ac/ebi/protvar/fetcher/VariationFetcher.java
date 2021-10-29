package uk.ac.ebi.protvar.fetcher;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.converter.VariationAPI2VariationConverter;
import uk.ac.ebi.protvar.model.api.DataServiceVariation;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.protvar.model.response.Variation;
import uk.ac.ebi.protvar.repo.UniprotAPIRepo;

import static uk.ac.ebi.protvar.utils.Commons.notNullNotEmpty;

@Service
@AllArgsConstructor
public class VariationFetcher {
	private VariationAPI2VariationConverter converter;
	private UniprotAPIRepo uniprotAPIRepo;

	public List<Variation> fetchByAccession(String uniprotAccession, String proteinLocation) {
		DataServiceVariation[] dsv = uniprotAPIRepo.getVariationByAccession(uniprotAccession, proteinLocation);
		if (isNotEmpty(dsv)) {
			return dsv[0].getFeatures().stream()
				.filter(Objects::nonNull)
				.map(feature -> converter.convert(feature))
				.filter(f -> notNullNotEmpty(f.getAlternativeSequence()))
				.filter(f -> notNullNotEmpty(f.getWildType()))
				.collect(Collectors.toList());
		}
		return Collections.emptyList();
	}

	private boolean isNotEmpty(DataServiceVariation[] dsv) {
		return dsv != null && dsv.length > 0;
	}

	public PopulationObservation fetchPopulationObservation(String accession, String proteinLocation) {
		List<Variation> variations = fetchByAccession(accession, proteinLocation);

		PopulationObservation populationObservation = new PopulationObservation();
		populationObservation.setProteinColocatedVariant(variations);
		return populationObservation;
	}

}
