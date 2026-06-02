package uk.ac.ebi.protvar.types;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
/**
 * Codon enum representing the 64 possible RNA codons and their corresponding amino acids.
 * It provides methods to get possible single nucleotide variants (SNVs) and alternative amino acids
 * for a given codon, as well as methods for converting between DNA and RNA sequences.
 */
public enum Codon {
    AAA, AAC, AAG, AAU,
    ACA, ACC, ACG, ACU,
    AGA, AGC, AGG, AGU,
    AUA, AUC, AUG, AUU,
    CAA, CAC, CAG, CAU,
    CCA, CCC, CCG, CCU,
    CGA, CGC, CGG, CGU,
    CUA, CUC, CUG, CUU,
    GAA, GAC, GAG, GAU,
    GCA, GCC, GCG, GCU,
    GGA, GGC, GGG, GGU,
    GUA, GUC, GUG, GUU,
    UAA, UAC, UAG, UAU,
    UCA, UCC, UCG, UCU,
    UGA, UGC, UGG, UGU,
    UUA, UUC, UUG, UUU;

    public static final Set<String> RNA_LETTERS = new HashSet<>(Arrays.asList("A", "C", "G", "U"));
    ;
    private AminoAcid aa;

    private Map<Integer, Set<Codon>> possibleVariants = new HashMap<>();

    public enum Change {
        SNV,
        DOUBLE,
        TRIPLE;

        public static Change byCount(int i) {
            switch (i) {
                case 1:
                    return SNV;
                case 2:
                    return DOUBLE;
                case 3:
                    return TRIPLE;
            }
            return null;
        }
    }

    public static Codon.Change type(Codon from, Codon to) {
        int count = 0;
        for (int pos = 0; pos < 3; pos++) {
            if (from.name().charAt(pos) != to.name().charAt(pos)) {
                count += 1;
            }
        }
        return Change.byCount(count);
    }

    static {
        AAA.aa = AminoAcid.LYS; AAC.aa = AminoAcid.ASN; AAG.aa = AminoAcid.LYS; AAU.aa = AminoAcid.ASN;
        ACA.aa = AminoAcid.THR; ACC.aa = AminoAcid.THR; ACG.aa = AminoAcid.THR; ACU.aa = AminoAcid.THR;
        AGA.aa = AminoAcid.ARG; AGC.aa = AminoAcid.SER; AGG.aa = AminoAcid.ARG; AGU.aa = AminoAcid.SER;
        AUA.aa = AminoAcid.ILE; AUC.aa = AminoAcid.ILE; AUG.aa = AminoAcid.MET; AUU.aa = AminoAcid.ILE;
        CAA.aa = AminoAcid.GLN; CAC.aa = AminoAcid.HIS; CAG.aa = AminoAcid.GLN; CAU.aa = AminoAcid.HIS;
        CCA.aa = AminoAcid.PRO; CCC.aa = AminoAcid.PRO; CCG.aa = AminoAcid.PRO; CCU.aa = AminoAcid.PRO;
        CGA.aa = AminoAcid.ARG; CGC.aa = AminoAcid.ARG; CGG.aa = AminoAcid.ARG; CGU.aa = AminoAcid.ARG;
        CUA.aa = AminoAcid.LEU; CUC.aa = AminoAcid.LEU; CUG.aa = AminoAcid.LEU; CUU.aa = AminoAcid.LEU;
        GAA.aa = AminoAcid.GLU; GAC.aa = AminoAcid.ASP; GAG.aa = AminoAcid.GLU; GAU.aa = AminoAcid.ASP;
        GCA.aa = AminoAcid.ALA; GCC.aa = AminoAcid.ALA; GCG.aa = AminoAcid.ALA; GCU.aa = AminoAcid.ALA;
        GGA.aa = AminoAcid.GLY; GGC.aa = AminoAcid.GLY; GGG.aa = AminoAcid.GLY; GGU.aa = AminoAcid.GLY;
        GUA.aa = AminoAcid.VAL; GUC.aa = AminoAcid.VAL; GUG.aa = AminoAcid.VAL; GUU.aa = AminoAcid.VAL;
        UAA.aa = AminoAcid.TER; UAC.aa = AminoAcid.TYR; UAG.aa = AminoAcid.TER; UAU.aa = AminoAcid.TYR;
        UCA.aa = AminoAcid.SER; UCC.aa = AminoAcid.SER; UCG.aa = AminoAcid.SER; UCU.aa = AminoAcid.SER;
        UGA.aa = AminoAcid.TER; UGC.aa = AminoAcid.CYS; UGG.aa = AminoAcid.TRP; UGU.aa = AminoAcid.CYS;
        UUA.aa = AminoAcid.LEU; UUC.aa = AminoAcid.PHE; UUG.aa = AminoAcid.LEU; UUU.aa = AminoAcid.PHE;
    }

    static {
        for (Codon codon : Codon.values()) {
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

                Set<Codon> variants = new HashSet<>();
                for (String s : RNA_LETTERS) {
                    if (!s.equals(String.valueOf(ref))) {
                        variants.add(Codon.valueOf(firstPart + s + remainder));
                    }
                }
                codon.possibleVariants.put(pos+1, variants);
            }
        }
    }

    /*
    get a set of possible SNVs for this RNA codon is the change is at position p
     */
    public Set<Codon> getSNVs(int pos) {
        return possibleVariants.get(pos);
    }

    /*
    get a set of possible SNVs for this RNA codon
     */
    public Set<Codon> getSNVs() {
        return possibleVariants.values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public Set<AminoAcid> getAltAAs(int pos) {
        Set<AminoAcid> aaSet = new HashSet<>();
        for (Codon snv : possibleVariants.get(pos))
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

    public static Codon fromDNA(String seq) {
        return Codon.valueOf(seq.replace("T", "U"));
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
    public static Codon reverse(Codon rnaCodon) {
        return Codon.valueOf(reverse(rnaCodon.name()));
    }


}