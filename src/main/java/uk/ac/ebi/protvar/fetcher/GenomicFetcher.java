package uk.ac.ebi.protvar.fetcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.converter.CoordinateAPI2GeneConverter;
import uk.ac.ebi.protvar.exception.ServiceException;
import uk.ac.ebi.protvar.model.Gene;
import uk.ac.ebi.protvar.model.LocationRange;
import uk.ac.ebi.protvar.model.UserInput;
import uk.ac.ebi.protvar.model.api.DataServiceCoordinate;
import uk.ac.ebi.protvar.repo.UniprotAPIRepo;

@Service
@AllArgsConstructor
public class GenomicFetcher {

	private static final Logger logger = LoggerFactory.getLogger(GenomicFetcher.class);
	private static final String COORDINATE_API_ERROR = "Coordinate API error";

	private UniprotAPIRepo uniprotAPIRepo;
	private CoordinateAPI2GeneConverter converter;

	public Map<String, Gene> fetch(final UserInput pInput) throws ServiceException {
		logger.info("Processing Gene -> {}", pInput.getFormattedInputString());
		DataServiceCoordinate[] dataServiceCoordinates;
		Map<String, Gene> accessionGeneMap = new HashMap<>();
		try {
			dataServiceCoordinates = uniprotAPIRepo.getGene(pInput);
		} catch (Exception ex) {
			String message = "input:" + pInput + " exception: " + ex.getMessage();
			throw new ServiceException(COORDINATE_API_ERROR, message);
		}

		for (DataServiceCoordinate dsc : dataServiceCoordinates) {
			Gene gene = converter.convert(dsc, pInput.getStart(), pInput.getStart());
			accessionGeneMap.put(dsc.getAccession(), gene);
		}
		return accessionGeneMap;
	}

	public Map<String, List<Gene>> searchGene(final String geneName, final String chromosome, final int offset,
			final int pageSize, final String location) {
		Map<String, List<Gene>> accessionGenesMap = new HashMap<>();
		DataServiceCoordinate[] dscs = uniprotAPIRepo.getCoordinates(geneName, chromosome, offset, pageSize, location);
		for (DataServiceCoordinate dsc : dscs) {
			List<Gene> genes = new ArrayList<>();
			List<LocationRange> ranges = new ArrayList<>();
			dsc.getGnCoordinate().forEach(gnCoordinate -> {
				long genomicStart = gnCoordinate.getGenomicLocation().getStart();
				long genomicEnd = gnCoordinate.getGenomicLocation().getEnd();
				LocationRange range = LocationRange.builder().start(genomicStart).end(genomicEnd).build();
				// To avoid duplicate coordinate with same range in the genes list
				if (!ranges.contains(range)) {
					ranges.add(range);
					genes.add(converter.convert(dsc, genomicStart, genomicEnd));
				}
			});
			accessionGenesMap.put(dsc.getAccession(), genes);
		}
		return accessionGenesMap;
	}
}
