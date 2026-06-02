package uk.ac.ebi.protvar.types;

import lombok.Getter;

import java.util.*;

@Getter
/**
 * AminoAcid enum representing the 20 standard amino acids, their one-letter and three-letter codes,
 * and their names. It also includes extended amino acids and provides methods for various operations
 * related to amino acids.
 */
public enum AminoAcid {
    // standard AAs (20) + stop codon, matching amino_acid table
    ALA("A", "Ala", "Alanine"),
    CYS("C", "Cys", "Cysteine"),
    ASP("D", "Asp", "Aspartic acid"),
    GLU("E", "Glu", "Glutamic acid"),
    PHE("F", "Phe", "Phenylalanine"),
    GLY("G", "Gly", "Glycine"),
    HIS("H", "His", "Histidine"),
    ILE("I", "Ile", "Isoleucine"),
    LYS("K", "Lys", "Lysine"),
    LEU("L", "Leu", "Leucine"),
    MET("M", "Met", "Methionine"),
    ASN("N", "Asn", "Asparagine"),
    PRO("P", "Pro", "Proline"),
    GLN("Q", "Gln", "Glutamine"),
    ARG("R", "Arg", "Arginine"),
    SER("S", "Ser", "Serine"),
    THR("T", "Thr", "Threonine"),
    VAL("V", "Val", "Valine"),
    TRP("W", "Trp", "Tryptophan"),
    TYR("Y", "Tyr", "Tyrosine"),
    TER("*", "Ter", "Stop codon"),

    /* Extended/non-standard AAs - from old AminoAcidsThreeLetter enum */
    ASX("B", "Asx", "Asparagine or aspartate", true),
    GLX("Z", "Glx", "Glutamine or glutamate", true),
    SEC("U", "Sec", "Selenocysteine", true),
    UNK("X", "Unk", "Any / unknown", true),

    /* extended AAs - not included before */
    PYL("O", "Pyl", "Pyrrolysine", true),
    XLE("J", "Xle", "Leucine or isoleucine", true)
    ;

    public String formatted() {
        return threeLetter + " (" + oneLetter +")";
    }
    private String oneLetter;
    private String threeLetter;
    private String fullName;
    private boolean extended;

    private Set<Codon> rnaCodons = new HashSet<>();
    private Map<AminoAcid, Set<Integer>> altAACodonPositions = new HashMap<>();

    public final static Set<String> VALID_AA1 = new HashSet<>();
    public final static Set<String> VALID_AA3 = new HashSet<>();

    static {
        // amino acid is encoded by the rna codons
        Arrays.stream(AminoAcid.standardValues())
                .forEach(aminoAcid -> {
                    Arrays.stream(Codon.values()).forEach(rnaCodon -> {
                        if (rnaCodon.getAa() != null && rnaCodon.getAa().equals(aminoAcid))
                            aminoAcid.rnaCodons.add(rnaCodon);
                    });
                });

        Arrays.stream(AminoAcid.standardValues())
                .forEach(aminoAcid -> {
                    aminoAcid.rnaCodons.stream().forEach(rnaCodon -> {
                        for (Map.Entry<Integer, Set<Codon>> entry : rnaCodon.getPossibleVariants().entrySet()) {
                            Integer pos = entry.getKey();
                            Set<Codon> variantCodons = entry.getValue();
                            for (Codon variantCodon : variantCodons) {
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

    AminoAcid(String oneLetter, String threeLetter, String fullName) {
        this.oneLetter = oneLetter;
        this.threeLetter = threeLetter;
        this.fullName = fullName;
    }

    AminoAcid(String oneLetter, String threeLetter, String fullName, Boolean extended) {
        this(oneLetter, threeLetter, fullName);
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
            if (aa.getOneLetter().equalsIgnoreCase(oneLetter))
                return aa;
        }
        //throw new UnexpectedUseCaseException(oneLetter + " is invalid one letter amino acid");
        return AminoAcid.UNK;
    }
    public static AminoAcid fromThreeLetter(String threeLetter) {
        for (AminoAcid aa : AminoAcid.values()) {
            if (aa.getThreeLetter().equalsIgnoreCase(threeLetter))
                return aa;
        }
        //throw new UnexpectedUseCaseException(oneLetter + " is invalid one letter amino acid");
        return AminoAcid.UNK;
    }

    public static AminoAcid fromOneOrThreeLetter(String str) {
        for (AminoAcid aa : AminoAcid.values()) {
            if (aa.getOneLetter().equalsIgnoreCase(str) || aa.getThreeLetter().equalsIgnoreCase(str))
                return aa;
        }
        //throw new UnexpectedUseCaseException(oneLetter + " is invalid one letter amino acid");
        return AminoAcid.UNK;
    }

    public static String oneLetter(String str) {
        if (str != null && !str.isEmpty()) {
            AminoAcid aa = AminoAcid.fromOneOrThreeLetter(str);
            if (aa != null && aa != AminoAcid.UNK) {
                return aa.getOneLetter();
            }
        }
        return null;
    }

    public Set<Integer> getChangePos(AminoAcid alt) {
        // Get all RNA codons that encode ref (this) amino acid
        // For each RNA codon that encodes the ref (this) amino acid,
        // get AA at each possible position
        Set<Integer> codonChangePositions = new HashSet<>();
        for (Codon codon : this.getRnaCodons()) {
            Map<Integer, Set<Codon>> variantMap = codon.getPossibleVariants();
            for (Integer pos : variantMap.keySet()) {
                Set<Codon> variantCodons = variantMap.get(pos);
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

    public static boolean isSNV(AminoAcid ref, AminoAcid alt) {
        Set<AminoAcid> allPossibleAltAAs = new HashSet();
        // ref is encoded by a set of codons
        Set<Codon> codons = ref.getRnaCodons();
        for (Codon codon : codons) {
            Set<AminoAcid> possibleAltAAs = codon.getAltAAs();
            allPossibleAltAAs.addAll(possibleAltAAs);
        }
        return allPossibleAltAAs.contains(alt);
    }
}