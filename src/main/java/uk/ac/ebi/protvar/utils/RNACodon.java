package uk.ac.ebi.protvar.utils;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public enum RNACodon {
    UUU, UCU, UAU, UGU,
    UUC, UCC, UAC, UGC,
    UUA, UCA, UAA, UGA,
    UUG, UCG, UAG, UGG,
    CUU, CCU, CAU, CGU,
    CUC, CCC, CAC, CGC,
    CUA, CCA, CAA, CGA,
    CUG, CCG, CAG, CGG,
    AUU, ACU, AAU, AGU,
    AUC, ACC, AAC, AGC,
    AUA, ACA, AAA, AGA,
    AUG, ACG, AAG, AGG,
    GUU, GCU, GAU, GGU,
    GUC, GCC, GAC, GGC,
    GUA, GCA, GAA, GGA,
    GUG, GCG, GAG, GGG;

    public static final char[] RNA = new char[] {'A', 'C', 'G', 'U'};
    private AminoAcid aa;
    private Map<Integer, List<RNACodon>> possibleVariants = new HashMap<>();

    static {
        UUU.aa = AminoAcid.PHE; UCU.aa = AminoAcid.SER; UAU.aa = AminoAcid.TYR; UGU.aa = AminoAcid.CYS;
        UUC.aa = AminoAcid.PHE; UCC.aa = AminoAcid.SER; UAC.aa = AminoAcid.TYR; UGC.aa = AminoAcid.CYS;
        UUA.aa = AminoAcid.LEU; UCA.aa = AminoAcid.SER; UAA.aa = AminoAcid.TER; UGA.aa = AminoAcid.TER;
        UUG.aa = AminoAcid.LEU; UCG.aa = AminoAcid.SER; UAG.aa = AminoAcid.TER; UGG.aa = AminoAcid.TRP;
        CUU.aa = AminoAcid.LEU; CCU.aa = AminoAcid.PRO; CAU.aa = AminoAcid.HIS; CGU	.aa = AminoAcid.ARG;
        CUC.aa = AminoAcid.LEU; CCC.aa = AminoAcid.PRO; CAC.aa = AminoAcid.HIS; CGC	.aa = AminoAcid.ARG;
        CUA.aa = AminoAcid.LEU; CCA.aa = AminoAcid.PRO; CAA.aa = AminoAcid.GLN; CGA	.aa = AminoAcid.ARG;
        CUG.aa = AminoAcid.LEU; CCG.aa = AminoAcid.PRO; CAG.aa = AminoAcid.GLN; CGG	.aa = AminoAcid.ARG;
        AUU.aa = AminoAcid.ILE; ACU.aa = AminoAcid.THR; AAU.aa = AminoAcid.ASN; AGU	.aa = AminoAcid.SER;
        AUC.aa = AminoAcid.ILE; ACC.aa = AminoAcid.THR; AAC.aa = AminoAcid.ASN; AGC	.aa = AminoAcid.SER;
        AUA.aa = AminoAcid.ILE; ACA.aa = AminoAcid.THR; AAA.aa = AminoAcid.LYS; AGA	.aa = AminoAcid.ARG;
        AUG.aa = AminoAcid.MET; ACG.aa = AminoAcid.THR; AAG.aa = AminoAcid.LYS; AGG	.aa = AminoAcid.ARG;
        GUU.aa = AminoAcid.VAL; GCU.aa = AminoAcid.ALA; GAU.aa = AminoAcid.ASP; GGU	.aa = AminoAcid.GLY;
        GUC.aa = AminoAcid.VAL; GCC.aa = AminoAcid.ALA; GAC.aa = AminoAcid.ASP; GGC	.aa = AminoAcid.GLY;
        GUA.aa = AminoAcid.VAL; GCA.aa = AminoAcid.ALA; GAA.aa = AminoAcid.GLU; GGA	.aa = AminoAcid.GLY;
        GUG.aa = AminoAcid.VAL; GCG.aa = AminoAcid.ALA; GAG.aa = AminoAcid.GLU; GGG	.aa = AminoAcid.GLY;

        for (RNACodon codon : RNACodon.values()) {
            /*    pos   1   2   3
                  ref   U   U   U
                  alt  ~U  ~U  ~U  (~U=ACG)
            UUU   ->  _UU U_U UU_
                      _UU U_U UU_
                      _UU U_U UU_

             */
            for (int pos = 0; pos < 3; pos++) {
                String triplets = codon.name();
                char ref = triplets.charAt(pos);
                String firstPart = triplets.substring(0, pos);
                String remainder = triplets.substring(pos+1);

                List<RNACodon> variants = new ArrayList<>();
                for (char c : RNA) {
                    if (c != ref) {
                        variants.add(RNACodon.valueOf(firstPart + c + remainder));
                    }
                }
                codon.possibleVariants.put(pos+1, variants);
            }
        }
    }

    public List<RNACodon> getSNVs(int pos) {
        return possibleVariants.get(pos);
    }
    public List<RNACodon> getSNVs() {
        return possibleVariants.values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public Set<AminoAcid> getVariantAAs(int pos) {
        Set<AminoAcid> aaSet = new HashSet<>();
        for (RNACodon snv : possibleVariants.get(pos))
            aaSet.add(snv.aa);
        return aaSet;
    }

    public Set<AminoAcid> getVariantAAs() {
        Set<AminoAcid> aaSet = new HashSet<>();
        for (int pos : possibleVariants.keySet()) {
            aaSet.addAll(getVariantAAs(pos));
        }
        return aaSet;
    }

    public static RNACodon fromDNA(String seq) {
        return RNACodon.valueOf(seq.replace("T", "U"));
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
    public static RNACodon reverse(RNACodon rnaCodon) {
        return RNACodon.valueOf(reverse(rnaCodon.name()));
    }


}