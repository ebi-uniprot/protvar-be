package uk.ac.ebi.pepvep.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import uk.ac.ebi.pepvep.model.Gene;
import uk.ac.ebi.pepvep.model.api.DataServiceCoordinate;
import uk.ac.ebi.pepvep.model.api.Exon;
import uk.ac.ebi.pepvep.model.api.GenomeLocation;
import uk.ac.ebi.pepvep.model.api.Position;

@Service
public class CoordinateAPI2GeneConverterTest {

	CoordinateAPI2GeneConverter converter = new CoordinateAPI2GeneConverter();

	@Test
	public void testApply() throws Exception {
		String data = Files.readString(Path.of("src/test/resources/coordinate_forward.json"));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		DataServiceCoordinate[] dsc = gson.fromJson(data, DataServiceCoordinate[].class);
		Gene gene = converter.convert(dsc[0], 89993420, 89993420);
		assertEquals(1, gene.getStrand());
		assertEquals("12/15", gene.getExon());
		assertEquals(0, gene.getOtherTranscripts().size());
		assertEquals(true, gene.isHasENSP());
		assertEquals(true, gene.isHasENST());
		assertNull(gene.getAllele());
		assertNull(gene.getHgvsg());
	}

	@Test
	public void testRedundantEnsts() throws Exception {
		String data = Files.readString(Path.of("src/test/resources/coordinate_forward.json"));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		DataServiceCoordinate[] dsc = gson.fromJson(data, DataServiceCoordinate[].class);
		Gene gene = converter.convert(dsc[3], 89993420, 89993420);
		assertEquals(1, gene.getStrand());
		assertEquals(1, gene.getOtherTranscripts().size());
		assertEquals("ENST00000393454", gene.getOtherTranscripts().get(0));
	}

	@Test
	public void testReverseStrand() throws Exception {
		String data = Files.readString(Path.of("src/test/resources/coordinate_reverse.json"));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		DataServiceCoordinate[] dsc = gson.fromJson(data, DataServiceCoordinate[].class);
		Gene gene = converter.convert(dsc[0], 43072000, 43072000);
		assertEquals(0, gene.getStrand());
		assertEquals("1/4", gene.getExon());
	}

	@ParameterizedTest
	@CsvSource({ "1-4,6/6", "4-1,6/6", "6-1,5-6/6", "7-16,3-5/6", "16-7,3-5/6", "31-5,''", "5-31,''","31-0,''", "0-31,''"})
	public void testReverseStrandExonRange(String location, String expected) throws Exception {
		// Exon -> Start	End
		// 			30		26
		// 			25		21
		// 			20		16
		// 			15		11
		// 			10		6
		// 			5		1
		String data = Files.readString(Path.of("src/test/resources/coordinate_reverse.json"));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		DataServiceCoordinate[] dsc = gson.fromJson(data, DataServiceCoordinate[].class);
		dsc[0].getGnCoordinate().get(0).getGenomicLocation().setExon(createExons(false));
		dsc[0].getGnCoordinate().get(0).getGenomicLocation().setStart(31);
		dsc[0].getGnCoordinate().get(0).getGenomicLocation().setEnd(0);
		Gene gene = converter.convert(dsc[0], Long.parseLong(location.split("-")[0]), 
				Long.parseLong(location.split("-")[1]));
		assertEquals(0, gene.getStrand());
		assertEquals(expected, gene.getExon());
	}

	@ParameterizedTest
	@CsvSource({ "1-4,1/6", "4-1,1/6", "6-1,1-2/6", "7-16,2-4/6", "31-5,''", "5-31,''","31-0,''", "0-31,''"})
	public void testForwardStrandExonRange(String location, String expected) throws Exception {
		// Exon -> Start	End
		// 			1		5
		// 			6		10
		// 			11		15
		// 			16		20
		// 			21		25
		// 			26		30	
		String data = Files.readString(Path.of("src/test/resources/coordinate_forward.json"));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		DataServiceCoordinate[] dsc = gson.fromJson(data, DataServiceCoordinate[].class);
		dsc[0].getGnCoordinate().get(0).getGenomicLocation().setExon(createExons(true));
		dsc[0].getGnCoordinate().get(0).getGenomicLocation().setStart(0);
		dsc[0].getGnCoordinate().get(0).getGenomicLocation().setEnd(31);
		Gene gene = converter.convert(dsc[0], Long.parseLong(location.split("-")[0]), 
				Long.parseLong(location.split("-")[1]));
		assertEquals(1, gene.getStrand());
		assertEquals(expected, gene.getExon());
	}

	List<Exon> createExons(boolean forwardStrand) {
		int exonRange = 5;
		List<Exon> exons = new ArrayList<>();
		if (forwardStrand) {
			for (int i = 1; i <= 30; i = i + exonRange) {
				Exon exon = new Exon();
				Position begin = new Position();
				begin.setPosition(i);
				Position end = new Position();
				end.setPosition(i+exonRange-1);
				GenomeLocation gnLoc = new GenomeLocation();
				gnLoc.setBegin(begin);
				gnLoc.setEnd(end);
				exon.setGenomeLocation(gnLoc);
				exons.add(exon);
			}
		} else {
			for (int i = 30; i >= 1; i = i - exonRange) {
				Exon exon = new Exon();
				Position begin = new Position();
				begin.setPosition(i);
				Position end = new Position();
				end.setPosition(i-exonRange+1);
				GenomeLocation gnLoc = new GenomeLocation();
				gnLoc.setBegin(begin);
				gnLoc.setEnd(end);
				exon.setGenomeLocation(gnLoc);
				exons.add(exon);
			}
		}
		return exons;
	}
}
