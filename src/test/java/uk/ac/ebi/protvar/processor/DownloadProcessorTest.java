package uk.ac.ebi.protvar.processor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.processor.CsvHeaders.Group;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DownloadProcessorTest {

  private static final String BASE = "Input,Notes,ID,Chromosome,Position,Ref,Alt,"
      + "Gene,Codon_change,Strand,CADD,AVI,Pangolin,Isoform,Other_isoforms,Transcripts,Protein,AA_position,AA_change,"
      + "Consequence,popEVE,AlphaMissense";

  @Nested
  class Header {
    @Test
    void variantAndMappingAlways() {
      assertEquals(BASE, String.join(",", CsvLayout.of(new DownloadRequest()).header()));
    }

    @Test
    void groupsAddedInOrder() {
      DownloadRequest r = new DownloadRequest();
      r.setPrediction(true);
      r.setStructure(true);
      assertEquals(BASE + ",Conservation,ESM1b,FoldX,Pockets,Interfaces,PDB_structures",
          String.join(",", CsvLayout.of(r).header()));
    }

    @Test
    void allGroups() {
      DownloadRequest r = new DownloadRequest();
      r.setFunction(true);
      r.setPrediction(true);
      r.setPopulation(true);
      r.setStructure(true);
      assertEquals(CsvHeaders.COLUMNS.size(), CsvLayout.of(r).header().length);
      assertEquals(45, CsvHeaders.COLUMNS.size());
    }

    @Test
    void onlyTableColumns() {
      DownloadRequest r = new DownloadRequest();
      r.setColumns(List.of("position", "gene", "isoform", "aaChange", "am", "details"));
      r.setPrediction(true);
      assertEquals("Input,Notes,Chromosome,Position,Ref,Alt,Gene,Isoform,Other_isoforms,AA_position,AA_change,"
              + "AlphaMissense,Conservation,ESM1b,FoldX,Pockets,Interfaces",
          String.join(",", CsvLayout.of(r).header()));
    }
  }

  @Nested
  class Row {
    @Test
    void missingGroupsAreNA() {
      DownloadRequest r = new DownloadRequest();
      r.setStructure(true);
      CsvLayout layout = CsvLayout.of(r);
      String[] row = layout.row(Map.of(Group.VARIANT, List.of("rs1", "note")));
      assertEquals(layout.header().length, row.length);
      assertEquals("rs1", row[0]);
      assertEquals("note", row[1]);
      for (int i = 2; i < row.length; i++) assertEquals("N/A", row[i]);
    }

    @Test
    void trimmedRowFollowsHeader() {
      DownloadRequest r = new DownloadRequest();
      r.setColumns(List.of("gene"));
      String[] row = CsvLayout.of(r).row(Map.of(
          Group.VARIANT, List.of("in", "notes", "id", "1", "100", "A", "G"),
          Group.MAPPING, List.of("BRCA1", "aCg/aTg", "+")));
      assertArrayEquals(new String[]{"in", "notes", "BRCA1"}, row);
    }
  }
}
