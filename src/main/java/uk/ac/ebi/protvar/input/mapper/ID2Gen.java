package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.id.ClinVarID;
import uk.ac.ebi.protvar.input.format.id.CosmicID;
import uk.ac.ebi.protvar.input.format.id.DbsnpID;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.ClinVar;
import uk.ac.ebi.protvar.model.data.Cosmic;
import uk.ac.ebi.protvar.model.data.Dbsnp;
import uk.ac.ebi.protvar.repo.ClinVarRepo;
import uk.ac.ebi.protvar.repo.CosmicRepo;
import uk.ac.ebi.protvar.repo.DbsnpRepo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class ID2Gen {
    private DbsnpRepo dbsnpRepo;
    private ClinVarRepo clinVarRepo;
    private CosmicRepo cosmicRepo;


    /**
     * Maps ID inputs (DBSNP) to their corresponding genomic coordinates.
     * It is possible that an ID gives multiple variants - in which case they are added to the genomicInputList.
     * @param groupedInputs
     */
    public void convert(Map<Type, List<UserInput>> groupedInputs) {
        if (groupedInputs.containsKey(Type.ID)) {
            List<UserInput> idInputs = groupedInputs.get(Type.ID);
            Map<Format, List<UserInput>> idGroups = idInputs.stream().collect(Collectors.groupingBy(UserInput::getFormat));

            dbsnpLookup(idGroups.get(Format.DBSNP));
            clinvarLookup(idGroups.get(Format.CLINVAR));
            cosmicLookup(idGroups.get(Format.COSMIC));
        }
    }

    public void dbsnpLookup(List<UserInput> dbsnpIdInputs) {
        if (dbsnpIdInputs == null || dbsnpIdInputs.isEmpty())
            return;

        Set<String> dbsnpIds = dbsnpIdInputs.stream()
                .map(i -> ((DbsnpID) i).getId()).collect(Collectors.toSet());

        Map<String, List<Dbsnp>> dbsnpMap = dbsnpRepo.findAllById(dbsnpIds).stream().collect(Collectors.groupingBy(Dbsnp::getId));

        dbsnpIdInputs.stream().map(i -> (DbsnpID) i).forEach(input -> {
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
                        input.addError(ErrorConstants.NO_MAPPING_DBSNP_ID);
                    }
                });
    }

    public void clinvarLookup(List<UserInput> clinvarIdInputs) {
        if (clinvarIdInputs == null || clinvarIdInputs.isEmpty())
            return;

        Map<String, List<UserInput>> clinvarIdTypeMap = clinvarIdInputs.stream().collect(Collectors.groupingBy(UserInput::getClinVarIDPrefix));

        Map<String, List<ClinVar>> clinvarRCVMap = null;
        Map<String, List<ClinVar>> clinvarVCVMap = null;

        if (clinvarIdTypeMap.get(ClinVarID.RCV) != null) {
            Set<String> rcvIds = clinvarIdTypeMap.get(ClinVarID.RCV).stream().map(i -> ((ClinVarID) i).getId()).collect(Collectors.toSet());
            clinvarRCVMap = clinVarRepo.getByRCV(rcvIds).stream().collect(Collectors.groupingBy(ClinVar::getRcv));
        }

        if (clinvarIdTypeMap.get(ClinVarID.VCV) != null) {
            Set<String> vcvIds = clinvarIdTypeMap.get(ClinVarID.VCV).stream().map(i -> ((ClinVarID) i).getId()).collect(Collectors.toSet());
            clinvarVCVMap = clinVarRepo.getByVCV(vcvIds).stream().collect(Collectors.groupingBy(ClinVar::getVcv));
        }

        for (UserInput ui : clinvarIdInputs) {
            ClinVarID input = (ClinVarID) ui;
            String id = input.getId();
            if (input.getClinVarIDPrefix().equals(ClinVarID.RCV) && clinvarRCVMap != null) {
                addDerivedGenomicInputs(clinvarRCVMap.get(id), input);
            } else if (input.getClinVarIDPrefix().equals(ClinVarID.VCV) && clinvarVCVMap != null) {
                addDerivedGenomicInputs(clinvarVCVMap.get(id), input);
            }
            if (input.getDerivedGenomicInputs().isEmpty()) {
                input.addError(ErrorConstants.NO_MAPPING_CLINVAR_ID);
            }
        }
    }

    public void cosmicLookup(List<UserInput> cosmicIdInputs) {
        if (cosmicIdInputs == null || cosmicIdInputs.isEmpty())
            return;

        Map<String, List<UserInput>> cosmicIdTypeMap = cosmicIdInputs.stream().collect(Collectors.groupingBy(UserInput::getCosmicIDPrefix));

        Set<String> ids = cosmicIdTypeMap.get(CosmicID.COSV) == null ? null :
                cosmicIdTypeMap.get(CosmicID.COSV).stream().map(i -> ((CosmicID) i).getId()).collect(Collectors.toSet());
        Set<String> legacyIds = Stream.concat(cosmicIdTypeMap.get(CosmicID.COSM) == null ? Stream.empty() : cosmicIdTypeMap.get(CosmicID.COSM).stream(),
                        cosmicIdTypeMap.get(CosmicID.COSN) == null ? Stream.empty() : cosmicIdTypeMap.get(CosmicID.COSN).stream())
                .map(i -> ((CosmicID) i).getId()).collect(Collectors.toSet());

        Map<String, List<Cosmic>> cosmicIdMap = cosmicRepo.getById(ids).stream().collect(Collectors.groupingBy(Cosmic::getId));
        Map<String, List<Cosmic>> cosmicLegacyIdMap = cosmicRepo.getByLegacyId(legacyIds).stream().collect(Collectors.groupingBy(Cosmic::getLegacyId));

        cosmicIdInputs.stream().map(i -> (CosmicID) i).forEach(input -> {
            String id = input.getId();
            if (cosmicIdMap != null && input.getCosmicIDPrefix().equals(CosmicID.COSV)) {
                addDerivedGenomicInputs(cosmicIdMap.get(id), input);
            } else if (cosmicLegacyIdMap != null && (input.getCosmicIDPrefix().equals(CosmicID.COSM) || input.getCosmicIDPrefix().equals(CosmicID.COSN))) {
                addDerivedGenomicInputs(cosmicLegacyIdMap.get(id), input);
            }
            if (input.getDerivedGenomicInputs().isEmpty()) {
                input.addError(ErrorConstants.NO_MAPPING_COSMIC_ID);
            }
        });


    }

    private void addDerivedGenomicInputs(List<ClinVar> clinVars, ClinVarID input) {
        if (clinVars != null && !clinVars.isEmpty()) {
            clinVars.forEach(clinVar -> {
                GenomicInput newInput = new GenomicInput(input.getInputStr());
                newInput.setChr(clinVar.getChr());
                newInput.setPos(clinVar.getPos());
                newInput.setRef(clinVar.getRef());
                newInput.setAlt(clinVar.getAlt());
                newInput.setId(input.getId());
                if (!input.getDerivedGenomicInputs().contains(newInput))
                    input.getDerivedGenomicInputs().add(newInput);
            });
        }
    }

    private void addDerivedGenomicInputs(List<Cosmic> cosmics, CosmicID input) {
        if (cosmics != null && !cosmics.isEmpty()) {
            cosmics.forEach(cosmic -> {
                GenomicInput newInput = new GenomicInput(input.getInputStr());
                newInput.setChr(cosmic.getChr());
                newInput.setPos(cosmic.getPos());
                newInput.setRef(cosmic.getRef());
                newInput.setAlt(cosmic.getAlt());
                newInput.setId(input.getId());
                if (!input.getDerivedGenomicInputs().contains(newInput))
                    input.getDerivedGenomicInputs().add(newInput);
            });
        }
    }

}
