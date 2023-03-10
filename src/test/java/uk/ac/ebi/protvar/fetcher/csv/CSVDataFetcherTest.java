package uk.ac.ebi.protvar.fetcher.csv;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;
import uk.ac.ebi.protvar.input.UserInput;

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
      var header = "User_input,Chromosome,Coordinate,ID,Reference_allele,Alternative_allele,Notes,Gene,Codon_change," +
        "Strand,CADD_phred_like_score,Canonical_isoform_transcripts,MANE_transcript,Uniprot_canonical_isoform_(non_canonical)," +
        "Alternative_isoform_mappings,Protein_name,Amino_acid_position,Amino_acid_change,Consequences," +
        "Residue_function_(evidence),Region_function_(evidence),Protein_existence_evidence,Protein_length," +
        "Entry_last_updated,Sequence_last_updated,Protein_catalytic_activity,Protein_complex,Protein_sub_cellular_location," +
        "Protein_family,Protein_interactions_PROTEIN(gene),Genomic_location,Cytogenetic_band," +
        "Other_identifiers_for_the_variant,Diseases_associated_with_variant,Variants_colocated_at_residue_position," +
        "Position_in_structures";
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