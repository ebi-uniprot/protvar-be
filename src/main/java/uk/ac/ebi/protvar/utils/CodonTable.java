package uk.ac.ebi.protvar.utils;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
/**
 * CodonTable enum representing the 64 possible RNA codons and their corresponding amino acids.
 * It provides methods to get possible single nucleotide variants (SNVs) and alternative amino acids
 * for a given codon, as well as methods for converting between DNA and RNA sequences.
 */
public enum CodonTable {
    AAA(AminoAcid.LYS), AAC(AminoAcid.ASN), AAG(AminoAcid.LYS), AAU(AminoAcid.ASN),
    ACA(AminoAcid.THR), ACC(AminoAcid.THR), ACG(AminoAcid.THR), ACU(AminoAcid.THR),
    AGA(AminoAcid.ARG), AGC(AminoAcid.SER), AGG(AminoAcid.ARG), AGU(AminoAcid.SER),
    AUA(AminoAcid.ILE), AUC(AminoAcid.ILE), AUG(AminoAcid.MET), AUU(AminoAcid.ILE),
    CAA(AminoAcid.GLN), CAC(AminoAcid.HIS), CAG(AminoAcid.GLN), CAU(AminoAcid.HIS),
    CCA(AminoAcid.PRO), CCC(AminoAcid.PRO), CCG(AminoAcid.PRO), CCU(AminoAcid.PRO),
    CGA(AminoAcid.ARG), CGC(AminoAcid.ARG), CGG(AminoAcid.ARG), CGU(AminoAcid.ARG),
    CUA(AminoAcid.LEU), CUC(AminoAcid.LEU), CUG(AminoAcid.LEU), CUU(AminoAcid.LEU),
    GAA(AminoAcid.GLU), GAC(AminoAcid.ASP), GAG(AminoAcid.GLU), GAU(AminoAcid.ASP),
    GCA(AminoAcid.ALA), GCC(AminoAcid.ALA), GCG(AminoAcid.ALA), GCU(AminoAcid.ALA),
    GGA(AminoAcid.GLY), GGC(AminoAcid.GLY), GGG(AminoAcid.GLY), GGU(AminoAcid.GLY),
    GUA(AminoAcid.VAL), GUC(AminoAcid.VAL), GUG(AminoAcid.VAL), GUU(AminoAcid.VAL),
    UAA(AminoAcid.TER), UAC(AminoAcid.TYR), UAG(AminoAcid.TER), UAU(AminoAcid.TYR),
    UCA(AminoAcid.SER), UCC(AminoAcid.SER), UCG(AminoAcid.SER), UCU(AminoAcid.SER),
    UGA(AminoAcid.TER), UGC(AminoAcid.CYS), UGG(AminoAcid.TRP), UGU(AminoAcid.CYS),
    UUA(AminoAcid.LEU), UUC(AminoAcid.PHE), UUG(AminoAcid.LEU), UUU(AminoAcid.PHE);
    public static final Set<String> RNA_LETTERS = new HashSet<>(Arrays.asList("A", "C", "G", "U"));
    ;
    private final AminoAcid aa;

    CodonTable(AminoAcid aa) {
        this.aa = aa;
    }
    private Map<Integer, Set<CodonTable>> possibleVariants = new HashMap<>();

    public enum Change {
        SNV,
        DOUBLE,
        TRIPLE;

        public static Change byCount(int i) {
            switch (i) {
                case 1:
                    return  SNV;
                case 2:
                    return DOUBLE;
                case 3:
                    return TRIPLE;
            }
            return null;
        }
    }

    static CodonTable.Change fromTo(CodonTable from, CodonTable to) {
        int count = 0;
        for (int pos = 0; pos < 3; pos++) {
            if (from.name().charAt(pos) != to.name().charAt(pos)) {
                count += 1;
            }
        }
        return Change.byCount(count);
    }

    static {
        for (CodonTable codon : CodonTable.values()) {
            /*    pos   1   2   3
                  ref   U   U   U
                  alt  ~U  ~U  ~U  (~U=ACG)
            UUU   ->  AUU UAU UUA
                      CUU UCU UUC
                      GUU UGU UUG

             */
            for (int pos = 0; pos < 3; pos++) {
                String triplets = codon.name();
                char ref = triplets.charAt(pos);
                String firstPart = triplets.substring(0, pos);
                String remainder = triplets.substring(pos+1);

                Set<CodonTable> variants = new HashSet<>();
                for (String s : RNA_LETTERS) {
                    if (!s.equals(String.valueOf(ref))) {
                        variants.add(CodonTable.valueOf(firstPart + s + remainder));
                    }
                }
                codon.possibleVariants.put(pos+1, variants);
            }
        }
    }

    /*
    get a set of possible SNVs for this RNA codon is the change is at position p
     */
    public Set<CodonTable> getSNVs(int pos) {
        return possibleVariants.get(pos);
    }

    /*
    get a set of possible SNVs for this RNA codon
     */
    public Set<CodonTable> getSNVs() {
        return possibleVariants.values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public Set<AminoAcid> getAltAAs(int pos) {
        Set<AminoAcid> aaSet = new HashSet<>();
        for (CodonTable snv : possibleVariants.get(pos))
            aaSet.add(snv.aa);
        return aaSet;
    }

    public Set<AminoAcid> getAltAAs() {
        Set<AminoAcid> aaSet = new HashSet<>();
        for (int pos : possibleVariants.keySet()) {
            aaSet.addAll(getAltAAs(pos));
        }
        return aaSet;
    }

    public static CodonTable fromDNA(String seq) {
        return CodonTable.valueOf(seq.replace("T", "U"));
    }

    public static String reverse(String codonStr) {
        String reverseStr = "";
        for (char c : codonStr.toCharArray()) {
            if (c == 'A')
                reverseStr += 'U';
            else if (c == 'U')
                reverseStr += 'A';
            else if (c == 'C')
                reverseStr += 'G';
            else if (c == 'G')
                reverseStr += 'C';
        }
        return reverseStr;
    }
    public static CodonTable reverse(CodonTable rnaCodon) {
        return CodonTable.valueOf(reverse(rnaCodon.name()));
    }


}