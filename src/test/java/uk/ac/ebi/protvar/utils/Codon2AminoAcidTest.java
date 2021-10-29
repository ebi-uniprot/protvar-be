package uk.ac.ebi.protvar.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class Codon2AminoAcidTest {
  private final List<String> all64Codons = List.of("TTT", "TTC", "TTA", "TTG", "CTT", "CTC", "CTA", "CTG", "ATT",
    "ATC", "ATA", "ATG", "GTT", "GTC", "GTA", "GTG", "TCT", "TCC", "TCA", "TCG", "CCT", "CCC", "CCA", "CCG", "ACT",
    "ACC", "ACA", "ACG", "GCT", "GCC", "GCA", "GCG", "TAT", "TAC", "TAA", "TAG", "CAT", "CAC", "CAA", "CAG", "AAT",
    "AAC", "AAA", "AAG", "GAT", "GAC", "GAA", "GAG", "TGT", "TGC", "TGA", "TGG", "CGT", "CGC", "CGA", "CGG", "AGT",
    "AGC", "AGA", "AGG", "GGT", "GGC", "GGA", "GGG");
  private final List<String> all64CodonsWithU = all64Codons.stream()
    .map(codon -> codon.replace("T", "U")).collect(Collectors.toList());

  @Test
  void lengthTest() {
    assertEquals(64, all64Codons.size());
  }

  @Test
  void shouldContainValidAminoAcid() {
    all64CodonsWithU.forEach(codon -> {
      String aminoAcidMapped = Codon2AminoAcid.getAminoAcid(codon);
      assertNotNull(aminoAcidMapped, "No amino acid found for codon " + codon);
      assertFalse(aminoAcidMapped.isEmpty(), "No amino acid found for codon " + codon);
      var aminoAcid = aminoAcidMapped.equals("*") ? "TER" : aminoAcidMapped;
      var validAA = Arrays.stream(AminoAcidsThreeLetter.values()).anyMatch(aatl -> aatl.name().equals(aminoAcid));
      assertTrue(validAA, "Amino acid " + aminoAcidMapped + " not found for codon " + codon);
    });
  }
}