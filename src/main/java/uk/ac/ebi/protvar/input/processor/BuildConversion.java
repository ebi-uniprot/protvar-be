package uk.ac.ebi.protvar.input.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
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

    @Autowired
    private ProtVarDataRepo protVarDataRepo;

    public void process(Map<Type, List<UserInput>> groupedInputs, String assemblyVersion, MappingResponse response) {
        List<Message> messages = response.getMessages();
        if (groupedInputs.containsKey(Type.GENOMIC)) {
            List<UserInput> genomicInputs = groupedInputs.get(Type.GENOMIC);
            boolean allGenomic = groupedInputs.size() == 1;

            /**   assembly
             *   |    |    \
             *   v    v     \
             *  null auto    v
             *   |    |     37or38
             *   | detect   |    |  \
             *   | |   \    |    |  /undetermined(assumes)
             *    \ \   v   v    v  v
             *     \ \  is37 ..> is38
             *      \_\___________^
             *
             *        ..> converts to
             */

            // null | auto | 37or38
            Assembly assembly = null;

            if (assemblyVersion == null) {
                messages.add(new Message(Message.MessageType.WARN, "Unspecified assembly version; defaulting to GRCh38"));
                assembly = Assembly.GRCH38;
            } else {
                if (assemblyVersion.equalsIgnoreCase("AUTO")) {
                    if (allGenomic) {
                        assembly = determineBuild(genomicInputs, messages); // -> 37 or 38
                    }
                    else {
                        messages.add(new Message(Message.MessageType.WARN, "Assembly auto-detect works for all-genomic inputs only; defaulting to GRCh38"));
                        assembly = Assembly.GRCH38;
                    }
                } else {
                    assembly = Assembly.of(assemblyVersion);
                    if (assembly == null) {
                        messages.add(new Message(Message.MessageType.WARN, "Unable to determine assembly version; defaulting to GRCh38"));
                        assembly = Assembly.GRCH38;
                    }
                }
            }

            if (assembly == Assembly.GRCH37) {
                messages.add(new Message(Message.MessageType.INFO, "Converting GRCh37 to GRCh38"));
                convert(genomicInputs);
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
            if (crossmaps == null || crossmaps.isEmpty()) {
                input.addError("No GRCh38 equivalent found for input coordinate");
            } else if (crossmaps.size()==1) {
                input.setPos(crossmaps.get(0).getGrch38Pos());
                input.setConverted(true);
            } else {
                input.addError("Multiple GRCh38 equivalents found for input coordinate");
            }
        });
    }

    // select distinct chr,grch38_pos,grch38_base from crossmap where (chr,grch38_pos,grch38_base) IN (('X',149498202,'C'),
    //('10',43118436,'A'),
    //('2',233760498,'G'))
    private Assembly determineBuild(List<UserInput> genomicInputs, List<Message> messages) {

        List<Object[]> chrPosRefList = new ArrayList<>();
        genomicInputs.stream().map(i -> (GenomicInput) i).forEach(input -> {
            chrPosRefList.add(new Object[]{input.getChr(), input.getPos(), input.getRef()});
        });

        double percentage38 = protVarDataRepo.getPercentageMatch(chrPosRefList, "38");
        if (percentage38 > 50) { // assumes 38
            messages.add(new Message(Message.MessageType.INFO, String.format("Determined assembly version is GRCh38 (%.2f%% match of user inputs)", percentage38)));
            return Assembly.GRCH38;
        } else {
            double percentage37 = protVarDataRepo.getPercentageMatch(chrPosRefList, "37");
            if (percentage37 > 50) { // assumes 37
                messages.add(new Message(Message.MessageType.INFO, String.format("Determined assembly version is GRCh38 (%.2f%% match of user inputs)", percentage37)));
                return Assembly.GRCH37;
            } else {
                String msg = String.format("Undetermined assembly version (%.2f%% GRCh38 match, %.2f%% GRCh37 match)", percentage38, percentage37);
                if (percentage37 > percentage38) {
                    messages.add(new Message(Message.MessageType.INFO, msg + " Assuming GRCh37"));
                    return Assembly.GRCH37;
                }
                else {
                    messages.add(new Message(Message.MessageType.INFO, msg + " Assuming GRCh38"));
                    return Assembly.GRCH38;
                }
            }
        }
    }
}
