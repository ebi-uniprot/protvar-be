package uk.ac.ebi.protvar.input.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.genomic.HGVSg;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.Crossmap;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BuildConversion {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildConversion.class);

    @Autowired
    private ProtVarDataRepo protVarDataRepo;

    public void process(Map<Type, List<UserInput>> groupedInputs, String assemblyVersion, MappingResponse response) {
        List<Message> messages = response.getMessages();
        if (groupedInputs.containsKey(Type.GENOMIC)) {
            List<UserInput> genomicInputs = groupedInputs.get(Type.GENOMIC);

            // separate inputs that need to be converted irrespective of user-specified assembly
            //List<UserInput> hgvsGs = new ArrayList<>();
            List<UserInput> hgvsGs37 = new ArrayList<>(); // to convert

            List<UserInput> nonHgvsGs = new ArrayList<>();

            genomicInputs.stream().filter(UserInput::isValid) // filter out invalid gen inputs
                    .forEach(i -> {
                if (i.getFormat() == Format.HGVS_GEN) {
                    //hgvsGs.add(i); // all hgvsGs, may be mix of 38 and 37 RefSeq accession
                    Boolean g37 = ((HGVSg) i).getRsAcc37();
                    if (g37 != null && g37)
                        hgvsGs37.add(i);
                }
                else
                    nonHgvsGs.add(i);
            });

            List<UserInput> convertList = new ArrayList<>();

            if (hgvsGs37.size() > 0)
                convertList.addAll(hgvsGs37);

            // what assembly has user specified
            // this will apply to the nonHgvsGs only
            boolean autodetect = assemblyVersion != null && assemblyVersion.equalsIgnoreCase("AUTO");

            if (nonHgvsGs.size() > 0) { // convert nonHgvsGs to 38, if condition met
                if (autodetect) {
                    /*if (percentage37Over50(nonHgvsGs)) {
                        convertList.addAll(nonHgvsGs);
                    }*/
                    if (nonHgvsGs.size() < 10) {
                        messages.add(new Message(Message.MessageType.INFO,
                                ErrorConstants.AUTO_DETECT_NOT_POSSIBLE.getErrorMessage()));
                    } else {
                        double match38 = buildPercentageMatch(nonHgvsGs, "38");
                        if (match38 > 75) {
                            // assumes 38, no conversion
                            messages.add(new Message(Message.MessageType.INFO,
                                    String.format(ErrorConstants.AUTO_DETECT_38.getErrorMessage(), match38)));
                        } else {
                            double match37 = buildPercentageMatch(nonHgvsGs, "37");
                            if (match37 > 75) {
                                // assumes 37, convert to 38
                                messages.add(new Message(Message.MessageType.INFO,
                                        String.format(ErrorConstants.AUTO_DETECT_37.getErrorMessage(), match37)));
                                convertList.addAll(nonHgvsGs);
                            } else {
                                messages.add(new Message(Message.MessageType.INFO,
                                        String.format(ErrorConstants.AUTO_DETECT_FAILED.getErrorMessage(), match38, match37)));
                            }
                        }
                    }
                } else {
                    Assembly assembly = Assembly.of(assemblyVersion);
                    if (assembly != null && assembly == Assembly.GRCH37) {
                        convertList.addAll(nonHgvsGs);
                    }
                }
            }

            if (convertList.size() > 0) {
                //messages.add(new Message(Message.MessageType.INFO,
                //        String.format(ErrorConstants.GEN_ASSEMBLY_CONVERT_INFO.getErrorMessage(),
                //                convertList.size(), FetcherUtils.pluralise(convertList.size()))));
                convert(convertList);
            }
        }
    }

    /**
     * Process genomic inputs
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

    // select distinct chr,grch38_pos,grch38_base from crossmap where (chr,grch38_pos,grch38_base) IN (('X',149498202,'C'),
    //('10',43118436,'A'),
    //('2',233760498,'G'))
    private Assembly determineBuild(List<UserInput> genomicInputs) {

        List<Object[]> chrPosRefList = new ArrayList<>();
        genomicInputs.stream().map(i -> (GenomicInput) i).forEach(input -> {
            chrPosRefList.add(new Object[]{input.getChr(), input.getPos(), input.getRef()});
        });

        double percentage38 = protVarDataRepo.getPercentageMatch(chrPosRefList, "38");
        if (percentage38 > 50) { // assumes 38
            LOGGER.info(String.format("Determined assembly version is GRCh38 (%.2f%% match of user inputs)", percentage38));
            return Assembly.GRCH38;
        } else {
            double percentage37 = protVarDataRepo.getPercentageMatch(chrPosRefList, "37");
            if (percentage37 > 50) { // assumes 37
                LOGGER.info(String.format("Determined assembly version is GRCh38 (%.2f%% match of user inputs)", percentage37));
                return Assembly.GRCH37;
            } else {
                String msg = String.format("Undetermined assembly version (%.2f%% GRCh38 match, %.2f%% GRCh37 match)", percentage38, percentage37);
                if (percentage37 > percentage38) {
                    LOGGER.info(msg + " Assuming GRCh37");
                    return Assembly.GRCH37;
                }
                else {
                    LOGGER.info(" Assuming GRCh38");
                    return Assembly.GRCH38;
                }
            }
        }
    }

    private boolean percentage37Over50(List<UserInput> nonHgvsGs) {
        List<Object[]> chrPosRefList = new ArrayList<>();
        nonHgvsGs.stream().map(i -> (GenomicInput) i).forEach(input -> {
            chrPosRefList.add(new Object[]{input.getChr(), input.getPos(), input.getRef()});
        });
        double percentage37 = protVarDataRepo.getPercentageMatch(chrPosRefList, "37");
        if (percentage37 > 50) { // assumes 37
            return true;
        }
        return false;
    }
    private double buildPercentageMatch(List<UserInput> nonHgvsGs, String build) {
        List<Object[]> chrPosRefList = new ArrayList<>();
        nonHgvsGs.stream().map(i -> (GenomicInput) i).forEach(input -> {
            chrPosRefList.add(new Object[]{input.getChr(), input.getPos(), input.getRef()});
        });
        return protVarDataRepo.getPercentageMatch(chrPosRefList, build);
    }
}
