package uk.ac.ebi.protvar.processor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uk.ac.ebi.protvar.fetcher.CachedInputHandler;
import uk.ac.ebi.protvar.mapper.AnnotationFetcher;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.fetcher.SearchInputHandler;
import uk.ac.ebi.protvar.fetcher.csv.CsvFunctionDataBuilder;
import uk.ac.ebi.protvar.fetcher.csv.CsvPopulationDataBuilder;
import uk.ac.ebi.protvar.fetcher.csv.CsvStructureDataBuilder;
import uk.ac.ebi.protvar.service.StructureService;
import uk.ac.ebi.protvar.service.InputCacheService;
import uk.ac.ebi.protvar.service.InputService;
import uk.ac.ebi.protvar.utils.Constants;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DownloadProcessorTest {

  private static final int TOTAL_CSV_COLUMNS = 44;
  DownloadProcessor mockDeps = new DownloadProcessor(
          mock(ThreadPoolTaskExecutor.class),
          mock(CsvFunctionDataBuilder.class),
          mock(CsvPopulationDataBuilder.class),
          mock(CsvStructureDataBuilder.class),
          mock(InputService.class),
          mock(InputCacheService.class),
          mock(CachedInputHandler.class),
          mock(SearchInputHandler.class),
          mock(InputMapper.class),
          mock(AnnotationFetcher.class),
          mock(StructureService.class));

  @Nested
  class Header {
    @Test
    void csvHeader() {
      var header = "User_input,Chromosome,Coordinate,ID,Reference_allele,Alternative_allele,Notes,Gene,Codon_change," +
        "Strand,CADD_phred_like_score,Canonical_isoform_transcripts,MANE_transcript,Uniprot_canonical_isoform_(non_canonical)," +
        "Alternative_isoform_mappings,Protein_name,Amino_acid_position,Amino_acid_change,Consequences," +
        "Residue_function_(evidence),Region_function_(evidence),Protein_existence_evidence,Protein_length," +
        "Entry_last_updated,Sequence_last_updated,Protein_catalytic_activity,Protein_complex,Protein_sub_cellular_location," +
        "Protein_family,Protein_interactions_PROTEIN(gene),Predicted_pockets(energy;per_vol;score;resids)," +
        "Predicted_interactions(chainA-chainB;a_resids;b_resids;pDockQ),Foldx_prediction(foldxDdg;plddt),Conservation_score," +
        "AlphaMissense_pathogenicity(class),EVE_score(class),ESM1b_score," +
        "Gnomad_allele_freq(ac;an;af),Genomic_location,Cytogenetic_band," +
        "Other_identifiers_for_the_variant,Diseases_associated_with_variant,Variants_colocated_at_residue_position," +
        "Position_in_structures";
      assertEquals(header, CsvHeaders.CSV_HEADER);
    }

    @Test
    void size() {
      assertEquals(TOTAL_CSV_COLUMNS, CsvHeaders.CSV_HEADER.split(Constants.COMMA).length);
    }
  }
}