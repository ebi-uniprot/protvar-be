package uk.ac.ebi.pepvep.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uk.ac.ebi.pepvep.model.Gene;
import uk.ac.ebi.pepvep.model.api.DSCGenomicLocation;
import uk.ac.ebi.pepvep.model.api.DSCGnCoordinate;
import uk.ac.ebi.pepvep.model.api.DataServiceCoordinate;
import uk.ac.ebi.pepvep.model.api.Exon;
import uk.ac.ebi.pepvep.utils.Constants;

@Service
public class CoordinateAPI2GeneConverter {

	private static final Logger logger = LoggerFactory.getLogger(CoordinateAPI2GeneConverter.class);

	public Gene convert(DataServiceCoordinate dsc, long start, long end) {
		logger.info("Accession in gene: " + dsc.getAccession());
		Gene gene = new Gene();
		gene.setStart(start);
		gene.setEnd(end);
		gene.setSource(Constants.HGNC);
		gene.setSymbol(dsc.getGene().get(0).getValue());
		List<DSCGnCoordinate> coordinates = getValidCoordinates(dsc.getGnCoordinate(), start, end);
		if (coordinates != null && !coordinates.isEmpty()) {
			DSCGnCoordinate gnCoordinate = coordinates.get(0);
			DSCGenomicLocation genomicLocation = gnCoordinate.getGenomicLocation();
			gene.setChromosome(genomicLocation.getChromosome());
			String ensemblGeneId = gnCoordinate.getEnsemblGeneId();
			gene.setEnsgId(ensemblGeneId);
			gene.setEnstId(gnCoordinate.getEnsemblTranscriptId());
			gene.setHgvsp(gnCoordinate.getEnsemblTranslationId());
			gene.setHasENSP(hasENSP(gnCoordinate.getEnsemblTranslationId()));
			gene.setHasENST(hasENST(gnCoordinate.getEnsemblTranscriptId()));
			setOtherIds(gene, coordinates);
			setStrand(gene, genomicLocation);
			gene.setExon(getExon(genomicLocation, start, end));
		}
		return gene;
	}

	private void setStrand(Gene gene, DSCGenomicLocation genomicLocation) {
		if (genomicLocation.isReverseStrand()) {
			gene.setStrand(0);
		} else {
			gene.setStrand(1);
		}
	}

	private void setOtherIds(Gene gene, List<DSCGnCoordinate> coordinates) {
		List<String> ensts = new ArrayList<>();
		List<String> ensps = new ArrayList<>();
		if (coordinates.size() > 1) {
			// Skip 1st coordinate
			for (int i = 1; i < coordinates.size(); i++) {
				ensts.add(coordinates.get(i).getEnsemblTranscriptId());
				ensps.add(coordinates.get(i).getEnsemblTranslationId());
			}
		}
		gene.setOtherTranscripts(ensts);
		gene.setOtherTranslations(ensps);
	}
	
	private String getExon(DSCGenomicLocation genomicLocation, long start, long end) {
		List<Exon> exons = genomicLocation.getExon();
		boolean reverseStrand = genomicLocation.isReverseStrand();
		int startIndex = IntStream.range(0, exons.size()).filter(i -> findPosition(start, exons.get(i), reverseStrand))
				.findFirst().orElse(-1);
		int endIndex = IntStream.range(0, exons.size()).filter(i -> findPosition(end, exons.get(i), reverseStrand))
				.findFirst().orElse(-1);

		return buildExon(exons.size(), startIndex, endIndex);
	}

	private String buildExon(int exonSize, int startIndex, int endIndex) {
		if (startIndex != -1 && endIndex != -1) {
			if (startIndex == endIndex) {
				return (startIndex + 1) + Constants.SLASH + exonSize;
			} else if (startIndex < endIndex) {
				return (startIndex + 1) + Constants.SPLITTER + (endIndex + 1) + Constants.SLASH + exonSize;
			} else {
				return  (endIndex + 1) + Constants.SPLITTER + (startIndex + 1) + Constants.SLASH + exonSize;
			}
		}
		return StringUtils.EMPTY;
	}

	private boolean findPosition(long position, Exon exon, boolean reverseStrand) {
		if(exon.getGenomeLocation().getBegin() == null) {
			exon.getGenomeLocation().setBegin(exon.getGenomeLocation().getPosition());
		}
		if(exon.getGenomeLocation().getEnd() == null) {
			exon.getGenomeLocation().setEnd(exon.getGenomeLocation().getPosition());
		}
		if (reverseStrand) {
			return exon.getGenomeLocation().getBegin().getPosition() >= position 
					&& exon.getGenomeLocation().getEnd().getPosition() <= position;
		}
		return exon.getGenomeLocation().getBegin().getPosition() <= position 
				&& exon.getGenomeLocation().getEnd().getPosition() >= position;
	}

	private List<DSCGnCoordinate> getValidCoordinates(List<DSCGnCoordinate> gnCoordinates, Long start, Long end) {
		if (gnCoordinates.get(0).getGenomicLocation().isReverseStrand()) {
			return gnCoordinates.stream().filter(gc -> filterReverseStrand(start, end, gc))
					.collect(Collectors.toList());
		} else {
			return gnCoordinates.stream().filter(gc -> filterForwardStrand(start, end, gc))
					.collect(Collectors.toList());
		}
	}

	private boolean filterForwardStrand(Long start, Long end, DSCGnCoordinate gc) {
		return gc.getGenomicLocation().getStart() <= start && gc.getGenomicLocation().getEnd() >= end;
	}

	private boolean filterReverseStrand(Long start, Long end, DSCGnCoordinate gc) {
		return gc.getGenomicLocation().getStart() >= end && gc.getGenomicLocation().getEnd() <= start;
	}

	private boolean hasENST(String ensemblTranscriptId) {
		return Objects.nonNull(ensemblTranscriptId);
	}

	private boolean hasENSP(String ensemblTranslationId) {
		return Objects.nonNull(ensemblTranslationId);
	}
}
