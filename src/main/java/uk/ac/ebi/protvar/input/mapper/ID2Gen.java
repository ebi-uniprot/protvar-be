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
import uk.ac.ebi.protvar.input.parser.variantid.ClinvarInputParser;
import uk.ac.ebi.protvar.input.parser.variantid.CosmicInputParser;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.ClinVarExtended;
import uk.ac.ebi.protvar.model.data.Cosmic;
import uk.ac.ebi.protvar.model.data.Dbsnp;
import uk.ac.ebi.protvar.repo.ClinVarRepo;
import uk.ac.ebi.protvar.repo.CosmicRepo;
import uk.ac.ebi.protvar.repo.DbsnpRepo;

import java.util.List;
import java.util.Map;
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
    public void map(Map<Type, List<UserInput>> groupedInputs) {
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

        List<Object[]> dbsnpIds = dbsnpIdInputs.stream()
                .map(i -> new Object[]{((DbsnpID) i).getId()}).collect(Collectors.toList());

        Map<String, List<Dbsnp>> dbsnpMap = dbsnpRepo.getById(dbsnpIds).stream().collect(Collectors.groupingBy(Dbsnp::getId));

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
                        input.addError(ErrorConstants.DBSNP_ID_NO_MAPPING);
                    }
                });
    }

    public void clinvarLookup(List<UserInput> clinvarIdInputs) {
        if (clinvarIdInputs == null || clinvarIdInputs.isEmpty())
            return;

        Map<String, List<UserInput>> clinvarIdTypeMap = clinvarIdInputs.stream().collect(Collectors.groupingBy(UserInput::getClinVarIDPrefix));

        Map<String, List<ClinVarExtended>> clinvarRCVMap = null; // Changed to ClinVarExtended
        Map<String, List<ClinVarExtended>> clinvarVCVMap = null; // Changed to ClinVarExtended

        if (clinvarIdTypeMap.get(ClinvarInputParser.RCV) != null) {
            //List<Object[]> rcvIds = clinvarIdTypeMap.get(ClinVarID.RCV).stream().map(i -> new Object[]{((ClinVarID) i).getId()}).collect(Collectors.toList());
            //clinvarRCVMap = clinVarRepo.getByRCV(rcvIds).stream().collect(Collectors.groupingBy(ClinVar::getRcv));
            List<String> rcvs = clinvarIdTypeMap.get(ClinvarInputParser.RCV).stream().map(i -> ((ClinVarID) i).getId()).collect(Collectors.toList());
            clinvarRCVMap = clinVarRepo.getByRCVMap(rcvs);
        }

        if (clinvarIdTypeMap.get(ClinvarInputParser.VCV) != null) {
            //List<Object[]> vcvIds = clinvarIdTypeMap.get(ClinVarID.VCV).stream().map(i -> new Object[]{((ClinVarID) i).getId()}).collect(Collectors.toList());
            //clinvarVCVMap = clinVarRepo.getByVCV(vcvIds).stream().collect(Collectors.groupingBy(ClinVar::getVcv));
            List<String> vcvs = clinvarIdTypeMap.get(ClinvarInputParser.VCV).stream().map(i -> ((ClinVarID) i).getId()).collect(Collectors.toList());
            clinvarVCVMap = clinVarRepo.getByVCVMap(vcvs);
        }

        for (UserInput ui : clinvarIdInputs) {
            ClinVarID input = (ClinVarID) ui;
            String id = input.getId();
            if (input.getClinVarIDPrefix().equals(ClinvarInputParser.RCV) && clinvarRCVMap != null) {
                addDerivedGenomicInputs(clinvarRCVMap.get(id), input);
            } else if (input.getClinVarIDPrefix().equals(ClinvarInputParser.VCV) && clinvarVCVMap != null) {
                addDerivedGenomicInputs(clinvarVCVMap.get(id), input);
            }
            if (input.getDerivedGenomicInputs().isEmpty()) {
                input.addError(ErrorConstants.CLINVAR_ID_NO_MAPPING);
            }
        }
    }

    public void cosmicLookup(List<UserInput> cosmicIdInputs) {
        if (cosmicIdInputs == null || cosmicIdInputs.isEmpty())
            return;

        Map<String, List<UserInput>> cosmicIdTypeMap = cosmicIdInputs.stream().collect(Collectors.groupingBy(UserInput::getCosmicIDPrefix));

        List<Object[]> ids = cosmicIdTypeMap.get(CosmicInputParser.COSV) == null ? null :
                cosmicIdTypeMap.get(CosmicInputParser.COSV).stream().map(i -> new Object[]{((CosmicID) i).getId()}).collect(Collectors.toList());
        List<Object[]> legacyIds = Stream.concat(cosmicIdTypeMap.get(CosmicInputParser.COSM) == null ? Stream.empty() : cosmicIdTypeMap.get(CosmicInputParser.COSM).stream(),
                        cosmicIdTypeMap.get(CosmicInputParser.COSN) == null ? Stream.empty() : cosmicIdTypeMap.get(CosmicInputParser.COSN).stream())
                .map(i -> new Object[]{((CosmicID) i).getId()}).collect(Collectors.toList());

        Map<String, List<Cosmic>> cosmicIdMap = cosmicRepo.getById(ids).stream().collect(Collectors.groupingBy(Cosmic::getId));
        Map<String, List<Cosmic>> cosmicLegacyIdMap = cosmicRepo.getByLegacyId(legacyIds).stream().collect(Collectors.groupingBy(Cosmic::getLegacyId));

        cosmicIdInputs.stream().map(i -> (CosmicID) i).forEach(input -> {
            String id = input.getId();
            if (cosmicIdMap != null && input.getCosmicIDPrefix().equals(CosmicInputParser.COSV)) {
                addDerivedGenomicInputs(cosmicIdMap.get(id), input);
            } else if (cosmicLegacyIdMap != null && (input.getCosmicIDPrefix().equals(CosmicInputParser.COSM) || input.getCosmicIDPrefix().equals(CosmicInputParser.COSN))) {
                addDerivedGenomicInputs(cosmicLegacyIdMap.get(id), input);
            }
            if (input.getDerivedGenomicInputs().isEmpty()) {
                input.addError(ErrorConstants.COSMIC_ID_NO_MAPPING);
            }
        });


    }

    // ClinVar changed to ClinVarExtended
    private void addDerivedGenomicInputs(List<ClinVarExtended> clinVars, ClinVarID input) {
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
