package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.id.DbsnpID;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.Dbsnp;
import uk.ac.ebi.protvar.repo.DbsnpRepo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ID2Gen {
    private DbsnpRepo dbsnpRepo;

    /**
     * Maps ID inputs (DBSNP) to their corresponding genomic coordinates.
     * It is possible that an ID gives multiple variants - in which case they are added to the genomicInputList.
     * @param idInputs
     */
    public void convert(List<UserInput> idInputs) {
        Set<String> dbsnpIds = idInputs.stream().filter(i -> i instanceof DbsnpID)
                .map(i -> ((DbsnpID) i).getId()).collect(Collectors.toSet());
        Map<String, List<Dbsnp>> dbsnpMap = dbsnpRepo.findAllById(dbsnpIds).stream().collect(Collectors.groupingBy(Dbsnp::getId));

        idInputs.stream().filter(i -> i instanceof DbsnpID)
                .map(i -> (DbsnpID) i).forEach(input -> {
            String id = input.getId();
            List<Dbsnp> dbsnps = dbsnpMap.get(id);
            if (dbsnps != null && !dbsnps.isEmpty()) {
                dbsnps.forEach(dbsnp -> {
                    String[] alts = dbsnp.getAlt().split(",");
                    for (String alt : alts) {
                        GenomicInput newInput = new GenomicInput(input.getInputStr());
                        newInput.setChr(dbsnp.getChr());
                        newInput.setPos(dbsnp.getPos());
                        newInput.setRef(dbsnp.getRef());
                        newInput.setAlt(alt);
                        newInput.setId(id);
                        if (!input.getDerivedGenomicInputs().contains(newInput))
                            input.getDerivedGenomicInputs().add(newInput);
                    }
                });
            }
            else {
                input.addError("Could not map ID to genomic coordinate(s).");
            }
        });
    }
}
