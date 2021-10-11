package uk.ac.ebi.pepvep.fetcher.csv;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.pepvep.utils.Constants;
import uk.ac.ebi.pepvep.fetcher.MappingFetcher;
import uk.ac.ebi.pepvep.model.UserInput;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CSVDataFetcherTest {
  private static final int TOTAL_CSV_COLUMNS = 36;
  CSVDataFetcher mockDeps = new CSVDataFetcher(mock(MappingFetcher.class), mock(CSVFunctionDataFetcher.class),
    mock(CSVPopulationDataFetcher.class), mock(CSVStructureDataFetcher.class));

  @Nested
  class Header {
    @Test
    void csvHeader() {
      var header = "USER INPUT,CHROMOSOME,COORDINATE,ID,REF,ALT,NOTES,GENE,CODON CHANGE,STRAND,CADD PHRED," +
        "TRANSCRIPTS OF THE CANONICAL ISOFORM,MANE TRANSCRIPT,UNIPROT CANONICAL ISOFORM,ALTERNATIVE ISOFORM DETAILS," +
        "PROTEIN NAME,AMINO ACID POSITION,AMINO ACID CHANGE,CONSEQUENCES,RESIDUE FUNCTION (EVIDENCE)," +
        "REGION FUNCTION (EVIDENCE),PROTEIN EXISTENCE,PROTEIN LENGTH,ENTRY LAST UPDATED,SEQUENCE LAST UPDATED," +
        "PROTEIN CATALYTIC ACTIVITY,PROTEIN COMPLEX,PROTEIN SUBCELLULAR LOCATION,PROTEIN FAMILY," +
        "PROTEIN INTERACTIONS - PROTEIN(GENE),GENOMIC LOCATION,CYTOGENETIC BAND,OTHER IDENTIFIERS FOR SAME VARIANT," +
        "DISEASES ASSOCIATED WITH VARIANT,VARIANTS CO-LOCATED AT SAME RESIDUE POSITION,STRUCTURE";
      assertEquals(header, CSVDataFetcher.CSV_HEADER);
    }

    @Test
    void size() {
      assertEquals(TOTAL_CSV_COLUMNS, CSVDataFetcher.CSV_HEADER.split(Constants.COMMA).length);
    }
  }

  @Nested
  class InvalidInput {
    @Test
    void columnSizeMatch() {
      var colsForInvalidInput = mockDeps.getCsvDataInvalidInput(UserInput.getInput("abc"));
      assertEquals(TOTAL_CSV_COLUMNS, colsForInvalidInput.length);
    }

    @Test
    void firstColAlwaysInput() {
      var input = "input";
      var colsForInvalidInput = mockDeps.getCsvDataInvalidInput(UserInput.getInput(input));
      assertEquals(input, colsForInvalidInput[0]);
    }

    @Test
    void allOtherColsWillBeNa_expectNoteCol() {
      var positionColIndex = 2;
      var noteColIndex = 6;
      var colsForInvalidInput = mockDeps.getCsvDataInvalidInput(UserInput.getInput("abc"));
      for (int i = 1; i < colsForInvalidInput.length; i++) {
        if (i != noteColIndex && i != positionColIndex)
          assertEquals(Constants.NA, colsForInvalidInput[i], String.valueOf(i));
        if (i == noteColIndex)
          assertEquals("Invalid chromosome abc|Position should be a number |Invalid reference |Invalid alternative ",
            colsForInvalidInput[i], String.valueOf(i));
        if (i == positionColIndex)
          assertEquals(-1L, Long.parseLong(colsForInvalidInput[i]), String.valueOf(i));
      }
    }
  }

  @Nested
  class MappingNotFound {
    private final String validInput = "x 1234 id G C";
    private final String[] colsMissingMapping = mockDeps.getCsvDataMappingNotFound(validInput);

    @Test
    void columnSizeMatch() {
      assertEquals(TOTAL_CSV_COLUMNS, colsMissingMapping.length);
    }

    @Test
    void firstColWillBeInput() {
      assertEquals(validInput, colsMissingMapping[0]);
    }

    @Test
    void secondColIsChromosome() {
      assertEquals("X", colsMissingMapping[1]);
    }

    @Test
    void thirdColIsPosition() {
      assertEquals("1234", colsMissingMapping[2]);
    }

    @Test
    void forthColIsId() {
      assertEquals("id", colsMissingMapping[3]);
    }

    @Test
    void fifthCol() {
      assertEquals("G", colsMissingMapping[4]);
    }

    @Test
    void sixCol() {
      assertEquals("C", colsMissingMapping[5]);
    }

    @Test
    void notesCol() {
      Assertions.assertEquals(Constants.NOTE_MAPPING_NOT_FOUND, colsMissingMapping[6]);
    }

    @Test
    void allOtherColsAreNa() {
      for (int i = 7; i < TOTAL_CSV_COLUMNS; i++)
        Assertions.assertEquals(Constants.NA, colsMissingMapping[i], String.valueOf(i));
    }
  }
}