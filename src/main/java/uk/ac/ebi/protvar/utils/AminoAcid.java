package uk.ac.ebi.protvar.utils;

import lombok.Getter;

import java.util.*;

@Getter
public enum AminoAcid {
    ALA("A", "Ala", "Alanine"),
    ARG("R", "Arg", "Arginine"),
    ASN("N", "Asn", "Asparagine"),
    ASP("D", "Asp", "Aspartic acid"),
    CYS("C", "Cys", "Cysteine"),
    GLN("Q", "Gln", "Glutamine"),
    GLU("E", "Glu", "Glutamic acid"),
    GLY("G", "Gly", "Glycine"),
    HIS("H", "His", "Histidine"),
    ILE("I", "Ile", "Isoleucine"),
    LEU("L", "Leu", "Leucine"),
    LYS("K", "Lys", "Lysine"),
    MET("M", "Met", "Methionine"),
    PHE("F", "Phe", "Phenylalanine"),
    PRO("P", "Pro", "Proline"),
    SER("S", "Ser", "Serine"),
    THR("T", "Thr", "Threonine"),
    TRP("W", "Trp", "Tryptophan"),
    TYR("Y", "Tyr", "Tyrosine"),
    VAL("V", "Val", "Valine"),
    TER("*", "Ter", "Termination (stop codon)"),

    /* extended/non-standard AAs - from old AminoAcidsThreeLetter enum */
    ASX("B", "Asx", "Asparagine or aspartate", true),
    GLX("Z", "Glx", "Glutamine or glutamate", true),
    SEC("U", "Sec", "Selenocysteine", true),
    UNK("X", "Unk", "Any / unknown", true),

    /* extended AAs - not included before */
    PYL("O", "Pyl", "Pyrrolysine", true),
    XLE("J", "Xle", "Leucine or isoleucine", true)
    ;

    public String formatted() {
        return threeLetters + " (" + oneLetter +")";
    }
    private String oneLetter;
    private String threeLetters;
    private String name;
    private boolean extended;

    private Set<RNACodon> rnaCodons = new HashSet<>();
    private Map<AminoAcid, Set<Integer>> altAACodonPositions = new HashMap<>();

    public final static Set<String> VALID_AA1 = new HashSet<>();
    public final static Set<String> VALID_AA3 = new HashSet<>();

    static {
        // amino acid is encoded by the rna codons
        Arrays.stream(AminoAcid.standardValues())
                .forEach(aminoAcid -> {
                    Arrays.stream(RNACodon.values()).forEach(rnaCodon -> {
                        if (rnaCodon.getAa() != null && rnaCodon.getAa().equals(aminoAcid))
                            aminoAcid.rnaCodons.add(rnaCodon);
                    });
                });

        Arrays.stream(AminoAcid.standardValues())
                .forEach(aminoAcid -> {
                    aminoAcid.rnaCodons.stream().forEach(rnaCodon -> {
                        for (Map.Entry<Integer, Set<RNACodon>> entry : rnaCodon.getPossibleVariants().entrySet()) {
                            Integer pos = entry.getKey();
                            Set<RNACodon> variantCodons = entry.getValue();
                            for (RNACodon variantCodon : variantCodons) {
                                AminoAcid aa = variantCodon.getAa();
                                if (aminoAcid.altAACodonPositions.containsKey(aa)) {
                                    aminoAcid.altAACodonPositions.get(aa).add(pos);
                                } else {
                                    Set posSet = new HashSet<Integer>();
                                    posSet.add(pos);
                                    aminoAcid.altAACodonPositions.put(aa, posSet);
                                }
                            }
                        }
                    });
                });

        Arrays.stream(AminoAcid.standardValues()).forEach(
                aminoAcid -> {
                    VALID_AA1.add(aminoAcid.getOneLetter());
                    VALID_AA3.add(aminoAcid.name());
                });
    }

    AminoAcid(String oneLetter, String threeLetters, String name) {
        this.oneLetter = oneLetter;
        this.threeLetters = threeLetters;
        this.name = name;
    }

    AminoAcid(String oneLetter, String threeLetters, String name, Boolean extended) {
        this(oneLetter, threeLetters, name);
        this.extended = extended;
    }

    public static AminoAcid[] standardValues() {
        return Arrays.stream(AminoAcid.values()).filter(aa -> !aa.extended).toArray(AminoAcid[]::new);
    }

    public static AminoAcid[] nonStandardValues() {
        return Arrays.stream(AminoAcid.values()).filter(aa -> aa.extended).toArray(AminoAcid[]::new);
    }

    public static AminoAcid fromOneLetter(String oneLetter) {
        for (AminoAcid aa : AminoAcid.values()) {
            if (aa.getOneLetter().equals(oneLetter))
                return aa;
        }
        //throw new UnexpectedUseCaseException(oneLetter + " is invalid one letter amino acid");
        return AminoAcid.UNK;
    }

    public Set<Integer> getChangePos(AminoAcid alt) {
        // Get all RNA codons that encode ref (this) amino acid
        // For each RNA codon that encodes the ref (this) amino acid,
        // get AA at each possible position
        Set<Integer> codonChangePositions = new HashSet<>();
        for (RNACodon codon : this.getRnaCodons()) {
            Map<Integer, Set<RNACodon>> variantMap = codon.getPossibleVariants();
            for (Integer pos : variantMap.keySet()) {
                Set<RNACodon> variantCodons = variantMap.get(pos);
                if (variantCodons.stream().filter(variantCodon -> variantCodon.getAa().equals(alt)).count() > 0)
                    codonChangePositions.add(pos);
            }
        }
        return codonChangePositions;
    }

    public Set<Integer> changedPositions(AminoAcid alt) {
        return this.getAltAACodonPositions().get(alt);
    }

    public static String getConsequence(AminoAcid ref, AminoAcid alt) {
        if (ref.equals(alt))
            return "synonymous";
        if (alt.equals(AminoAcid.TER))
            return "stop gained";
        return "missense";
    }

    public String description() {
        return "XXX is encoded by X codons (x,y,z)" +
                "There are x possible alternates for XXX incl. YYY, ZZZ";
    }
}