package uk.ac.ebi.protvar.converter;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.model.response.Isoform;
import uk.ac.ebi.protvar.model.response.Transcript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IsoformCanonicalCopiesTest {

  private static Isoform isoform(String acc, boolean canonical, int pos, String variantAA, String... ensts) {
    return Isoform.builder().accession(acc).canonical(canonical).isoformPosition(pos).refAA("ALA").variantAA(variantAA)
        .transcripts(List.of(ensts).stream().map(e -> Transcript.builder().enst(e).build()).toList()).build();
  }

  private static List<String> kept(Isoform... isoforms) {
    return IsoformConverter.withoutCanonicalCopies(List.of(isoforms)).stream().map(Isoform::getAccession).toList();
  }

  @Test
  void dropsCopyOfCanonical() {
    assertEquals(List.of("P1"), kept(isoform("P1", true, 5, "PRO", "T1"), isoform("P1-1", false, 5, "PRO", "T1")));
  }

  @Test
  void canonicalNamesItsCopies() {
    Isoform canonical = isoform("P1", true, 5, "PRO", "T1");
    kept(canonical, isoform("P1-1", false, 5, "PRO", "T1"), isoform("P1-2", false, 9, "PRO", "T1"));
    assertEquals(List.of("P1-1"), canonical.getSameAs());
  }

  @Test
  void keepsDifferentPositionChangeOrTranscripts() {
    Isoform canonical = isoform("P1", true, 5, "PRO", "T1", "T2");
    assertEquals(List.of("P1", "P1-2", "P1-3", "P1-4"), kept(canonical,
        isoform("P1-2", false, 9, "PRO", "T1", "T2"),
        isoform("P1-3", false, 5, "SER", "T1", "T2"),
        isoform("P1-4", false, 5, "PRO", "T1")));
  }

  @Test
  void noCanonicalKeepsAll() {
    assertEquals(List.of("P1-1", "P1-2"), kept(isoform("P1-1", false, 5, "PRO", "T1"), isoform("P1-2", false, 5, "PRO", "T1")));
  }
}
