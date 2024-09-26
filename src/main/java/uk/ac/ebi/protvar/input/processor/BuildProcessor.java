package uk.ac.ebi.protvar.input.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.genomic.Gnomad;
import uk.ac.ebi.protvar.input.format.genomic.HGVSg;
import uk.ac.ebi.protvar.input.format.genomic.VCF;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.Crossmap;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BuildProcessor {
    public static final int AUTO_DETECT_MIN_SIZE = 10;
    public static final int AUTO_DETECT_SAMPLE_SIZE = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildProcessor.class);

    @Autowired
    private ProtVarDataRepo protVarDataRepo;

    /**
     * Filter out non-genomic inputs (incl. hgvs inputs as the build for these are implicit).
     * @param inputs
     * @return
     */
    public List<UserInput> genomicInputs(List<String> inputs) {
        return inputs.stream()
                .map(inputStr -> {
                    if (GenomicInput.startsWithChromo(inputStr)) {
                        if (Gnomad.matchesPattern(inputStr)) // ^chr-pos-ref-alt$
                            return Gnomad.parse(inputStr);

                        if (VCF.matchesPattern(inputStr)) // ^chr pos id ref alt...
                            return VCF.parse(inputStr);

                        if (GenomicInput.matchesPattern(inputStr)) // ^chr pos( ref( alt)?)?$
                            return GenomicInput.parse(inputStr);
                    }
                    return GenomicInput.invalid(inputStr);
                })
                .filter(UserInput::isValid)
                .collect(Collectors.toList());
    }


    /**
     * Detect the build of the given list of genomic inputs, if minimum input threshold is met.
     * Fallbacks to GRCh38 if autodetect is inconclusive.
     * @param genomicInputs
     * @return
     */
    public InputBuild detect(List<UserInput> genomicInputs) {
        if (genomicInputs.isEmpty() || genomicInputs.size() < AUTO_DETECT_MIN_SIZE) {
            return new InputBuild(Assembly.GRCH38, new Message(Message.MessageType.INFO,
                    ErrorConstants.AUTO_DETECT_NOT_POSSIBLE.getErrorMessage()));
        } else {
            List<UserInput> sampleGenomicInputs = randomSublist(genomicInputs, AUTO_DETECT_SAMPLE_SIZE);
            double match38 = buildPercentageMatch(sampleGenomicInputs, "38");
            if (match38 > 75) {
                // assumes 38, no conversion
                return new InputBuild(Assembly.GRCH38, new Message(Message.MessageType.INFO,
                        String.format(ErrorConstants.AUTO_DETECT_38.getErrorMessage(), match38)));
            } else {
                double match37 = buildPercentageMatch(sampleGenomicInputs, "37");
                if (match37 > 75) {
                    // assumes 37, convert to 38
                    return new InputBuild(Assembly.GRCH37, new Message(Message.MessageType.INFO,
                            String.format(ErrorConstants.AUTO_DETECT_37.getErrorMessage(), match37)));
                } else {
                    return new InputBuild(Assembly.GRCH38, new Message(Message.MessageType.INFO,
                            String.format(ErrorConstants.AUTO_DETECT_FAILED.getErrorMessage(), match38, match37)));
                }
            }
        }
    }


    public void process(Map<Type, List<UserInput>> groupedInputs, InputParams params) {
        List<UserInput> genomicInputs = groupedInputs.get(Type.GENOMIC);
        if (genomicInputs == null || genomicInputs.isEmpty()) {
            return;
        }

        // Separate inputs that need to be converted irrespective of user-specified assembly e.g. HGVSg37
        List<UserInput> hgvsGs37 = new ArrayList<>(); // to convert
        List<UserInput> nonHgvsGs = new ArrayList<>();

        genomicInputs.stream()
                .filter(UserInput::isValid) // filter out invalid gen inputs
                .forEach(i -> {
                    if (i.getFormat() == Format.HGVS_GEN && Boolean.TRUE.equals(((HGVSg) i).getRsAcc37())) {
                        hgvsGs37.add(i);
                    } else {
                        nonHgvsGs.add(i);
                    }
            });

        List<UserInput> convertList = new ArrayList<>(hgvsGs37);

        if (!nonHgvsGs.isEmpty() && params.isConvert()) {
            convertList.addAll(nonHgvsGs);
        }

        if (!convertList.isEmpty()) {
            convert(convertList);
        }
    }

    /**
     * Convert GRCh37 genomic inputs
     * - this is required if an assembly conversion is needed
     * - note that if multiple equivalents are found, these are not added as new inputs but is considered invalid.
     * - genomic inputs may have multiple outputs for e.g. overlapping genes in same or both directions.
     * - the latter is tackled in the main mapping logic.
     * @param genomicInputs
     */
    private void convert(List<UserInput> genomicInputs) {

        List<Object[]> chrPos37 = new ArrayList<>();
        genomicInputs.stream().map(i -> (GenomicInput) i).forEach(input -> {
            chrPos37.add(new Object[]{input.getChr(), input.getPos()});
        });

        Map<String, List<Crossmap>> groupedCrossmaps = protVarDataRepo.getCrossmapsByChrPos37(chrPos37)
                .stream().collect(Collectors.groupingBy(Crossmap::getGroupByChrAnd37Pos));

        genomicInputs.stream().map(i -> (GenomicInput) i).forEach(input -> {

            String chr = input.getChr();
            Integer pos = input.getPos();
            List<Crossmap> crossmaps = groupedCrossmaps.get(chr+"-"+pos);

            if (crossmaps != null && crossmaps.size() > 0) {
                // We should expect 1 result, multiple mapping not possible based on
                // select chr, grch37_pos, count(*) from crossmap
                // group by chr, grch37_pos
                // having count(*) > 1 -- no result!
                input.setPos(crossmaps.get(0).getGrch38Pos());
                input.setConverted(true);
            } else {
                input.addError(ErrorConstants.GEN_ASSEMBLY_CONVERT_ERR_NOT_FOUND);
            }

        });
    }

    private double buildPercentageMatch(List<UserInput> nonHgvsGs, String build) {
        List<Object[]> chrPosRefList = new ArrayList<>();
        nonHgvsGs.stream().map(i -> (GenomicInput) i).forEach(input -> {
            chrPosRefList.add(new Object[]{input.getChr(), input.getPos(), input.getRef()});
        });
        return protVarDataRepo.getPercentageMatch(chrPosRefList, build);
    }

    public <T> List<T> randomSublist(List<T> originalList, int sublistSize) {
        // Create a copy of the original list
        List<T> copyList = new ArrayList<>(originalList);
        // Shuffle the copy list
        Collections.shuffle(copyList);
        // Ensure the sublist size is within the bounds of the original list
        sublistSize = Math.min(sublistSize, copyList.size());
        // Return the sublist of the specified size
        return copyList.subList(0, sublistSize);
    }
}