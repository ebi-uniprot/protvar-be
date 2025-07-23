package uk.ac.ebi.protvar.input.mapper;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.parser.variantid.ClinvarParser;
import uk.ac.ebi.protvar.input.parser.variantid.CosmicParser;
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
public class Id2Gen {
    private DbsnpRepo dbsnpRepo;
    private ClinVarRepo clinVarRepo;
    private CosmicRepo cosmicRepo;


    /**
     * Maps ID inputs (DBSNP) to their corresponding genomic coordinates.
     * It is possible that an ID gives multiple variants - in which case they are added to the genomicInputList.
     * @param groupedInputs
     */
    public void map(Map<VariantType, List<VariantInput>> groupedInputs) {
        if (groupedInputs.containsKey(VariantType.VARIANT_ID)) {
            List<VariantInput> idInputs = groupedInputs.get(VariantType.VARIANT_ID);
            Map<VariantFormat, List<VariantInput>> idGroups = idInputs.stream().collect(Collectors.groupingBy(VariantInput::getFormat));

            dbsnpLookup(idGroups.get(VariantFormat.DBSNP));
            clinvarLookup(idGroups.get(VariantFormat.CLINVAR));
            cosmicLookup(idGroups.get(VariantFormat.COSMIC));
        }
    }

    public void dbsnpLookup(List<VariantInput> dbsnpIdInputs) {
        if (dbsnpIdInputs == null || dbsnpIdInputs.isEmpty())
            return;

        List<Object[]> dbsnpIds = dbsnpIdInputs.stream()
                .map(i -> new Object[]{i.getInputStr()}).collect(Collectors.toList());

        Map<String, List<Dbsnp>> dbsnpMap = dbsnpRepo.getById(dbsnpIds).stream().collect(Collectors.groupingBy(Dbsnp::getId));

        dbsnpIdInputs.stream().forEach(input -> {
                    String id = input.getInputStr();
                    List<Dbsnp> dbsnps = dbsnpMap.get(id);
                    if (dbsnps != null && !dbsnps.isEmpty()) {
                        dbsnps.forEach(dbsnp -> {
                            String[] alts = dbsnp.getAlt().split(",");
                            for (String alt : alts) {
                                GenomicVariant newVariant = new GenomicVariant(dbsnp.getChr(), dbsnp.getPos(),
                                        dbsnp.getRef(), alt);
                                if (!input.getDerivedGenomicVariants().contains(newVariant))
                                    input.getDerivedGenomicVariants().add(newVariant);
                            }
                        });
                    }
                    else {
                        input.addError(ErrorConstants.DBSNP_ID_NO_MAPPING);
                    }
                });
    }

    public void clinvarLookup(List<VariantInput> clinvarIdInputs) {
        if (clinvarIdInputs == null || clinvarIdInputs.isEmpty())
            return;

        Map<String, List<VariantInput>> clinvarIdTypeMap = clinvarIdInputs.stream()
                .collect(Collectors.groupingBy(VariantInput::getIdPrefix));

        Map<String, List<ClinVarExtended>> clinvarRCVMap = null; // Changed to ClinVarExtended
        Map<String, List<ClinVarExtended>> clinvarVCVMap = null; // Changed to ClinVarExtended

        if (clinvarIdTypeMap.get(ClinvarParser.RCV) != null) {
            //List<Object[]> rcvIds = clinvarIdTypeMap.get(ClinVarID.RCV).stream().map(i -> new Object[]{((ClinVarID) i).getId()}).collect(Collectors.toList());
            //clinvarRCVMap = clinVarRepo.getByRCV(rcvIds).stream().collect(Collectors.groupingBy(ClinVar::getRcv));
            List<String> rcvs = clinvarIdTypeMap.get(ClinvarParser.RCV).stream().map(VariantInput::getInputStr).collect(Collectors.toList());
            clinvarRCVMap = clinVarRepo.getByRCVMap(rcvs.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toList())); // todo move this capitalisation earlier on, or in db query
        }

        if (clinvarIdTypeMap.get(ClinvarParser.VCV) != null) {
            //List<Object[]> vcvIds = clinvarIdTypeMap.get(ClinVarID.VCV).stream().map(i -> new Object[]{((ClinVarID) i).getId()}).collect(Collectors.toList());
            //clinvarVCVMap = clinVarRepo.getByVCV(vcvIds).stream().collect(Collectors.groupingBy(ClinVar::getVcv));
            List<String> vcvs = clinvarIdTypeMap.get(ClinvarParser.VCV).stream().map(VariantInput::getInputStr).collect(Collectors.toList());
            clinvarVCVMap = clinVarRepo.getByVCVMap(vcvs.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toList())); // todo same here!
        }

        for (VariantInput input : clinvarIdInputs) {
            String id = input.getInputStr();
            if (input.getIdPrefix().equals(ClinvarParser.RCV) && clinvarRCVMap != null) {
                addClinvarDerivedGenomicVariants(clinvarRCVMap.get(id), input);
            } else if (input.getIdPrefix().equals(ClinvarParser.VCV) && clinvarVCVMap != null) {
                addClinvarDerivedGenomicVariants(clinvarVCVMap.get(id), input);
            }
            if (input.getDerivedGenomicVariants().isEmpty()) {
                input.addError(ErrorConstants.CLINVAR_ID_NO_MAPPING);
            }
        }
    }

    public void cosmicLookup(List<VariantInput> cosmicIdInputs) {
        if (cosmicIdInputs == null || cosmicIdInputs.isEmpty())
            return;

        Map<String, List<VariantInput>> cosmicIdTypeMap = cosmicIdInputs.stream().collect(Collectors.groupingBy(VariantInput::getIdPrefix));

        List<Object[]> ids = cosmicIdTypeMap.get(CosmicParser.COSV) == null ? null :
                cosmicIdTypeMap.get(CosmicParser.COSV).stream().map(i -> new Object[]{i.getInputStr().toUpperCase()}).collect(Collectors.toList());
        List<Object[]> legacyIds = Stream.concat(cosmicIdTypeMap.get(CosmicParser.COSM) == null ? Stream.empty() : cosmicIdTypeMap.get(CosmicParser.COSM).stream(),
                        cosmicIdTypeMap.get(CosmicParser.COSN) == null ? Stream.empty() : cosmicIdTypeMap.get(CosmicParser.COSN).stream())
                .map(i -> new Object[]{i.getInputStr().toUpperCase()}).collect(Collectors.toList());

        Map<String, List<Cosmic>> cosmicIdMap = cosmicRepo.getById(ids).stream().collect(Collectors.groupingBy(Cosmic::getId));
        Map<String, List<Cosmic>> cosmicLegacyIdMap = cosmicRepo.getByLegacyId(legacyIds).stream().collect(Collectors.groupingBy(Cosmic::getLegacyId));

        cosmicIdInputs.stream().forEach(input -> {
            String id = input.getInputStr();
            if (cosmicIdMap != null && input.getIdPrefix().equals(CosmicParser.COSV)) {
                addCosmicDerivedGenomicVariants(cosmicIdMap.get(id), input);
            } else if (cosmicLegacyIdMap != null && (input.getIdPrefix().equals(CosmicParser.COSM) || input.getIdPrefix().equals(CosmicParser.COSN))) {
                addCosmicDerivedGenomicVariants(cosmicLegacyIdMap.get(id), input);
            }
            if (input.getDerivedGenomicVariants().isEmpty()) {
                input.addError(ErrorConstants.COSMIC_ID_NO_MAPPING);
            }
        });


    }

    // ClinVar changed to ClinVarExtended
    private void addClinvarDerivedGenomicVariants(List<ClinVarExtended> clinVars, VariantInput input) {
        if (clinVars != null && !clinVars.isEmpty()) {
            clinVars.forEach(clinVar -> {
                GenomicVariant newVariant = new GenomicVariant(clinVar.getChr(), clinVar.getPos(),
                        clinVar.getRef(), clinVar.getAlt());
                if (!input.getDerivedGenomicVariants().contains(newVariant))
                    input.getDerivedGenomicVariants().add(newVariant);
            });
        }
    }

    private void addCosmicDerivedGenomicVariants(List<Cosmic> cosmics, VariantInput input) {
        if (cosmics != null && !cosmics.isEmpty()) {
            cosmics.forEach(cosmic -> {
                GenomicVariant newVariant = new GenomicVariant(cosmic.getChr(), cosmic.getPos(),
                        cosmic.getRef(), cosmic.getAlt());
                if (!input.getDerivedGenomicVariants().contains(newVariant))
                    input.getDerivedGenomicVariants().add(newVariant);
            });
        }
    }

}
