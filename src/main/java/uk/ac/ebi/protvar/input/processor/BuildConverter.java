package uk.ac.ebi.protvar.input.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.Crossmap;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BuildConverter {

    @Autowired
    private ProtVarDataRepo protVarDataRepo;

    /**
     * Process genomic inputs
     * - this is required if an assembly conversion is needed
     * - note that if multiple equivalents are found, these are not added as new inputs but is considered invalid.
     * - genomic inputs may have multiple outputs for e.g. overlapping genes in same or both directions.
     * - the latter is tackled in the main mapping logic.
     * @param genomicInputs
     */
    public void convert(List<UserInput> genomicInputs) {

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
    public Assembly determineBuild(List<UserInput> genomicInputs, List<Message> messages) {

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
