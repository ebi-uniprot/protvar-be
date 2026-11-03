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
import uk.ac.ebi.protvar.repo.HumsavarRepo;
import uk.ac.ebi.protvar.model.data.Humsavar;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class Id2Gen {
    private DbsnpRepo dbsnpRepo;
    private ClinVarRepo clinVarRepo;
    private CosmicRepo cosmicRepo;
    private HumsavarRepo humsavarRepo;


    /**
     * Maps ID inputs to their variants. dbSNP, ClinVar and COSMIC IDs give genomic coordinates (an ID can give
     * several, all added to the input's derived genomic variants). UniProt VAR_ IDs give a protein change
     * (accession, position, ref/alt AA), which Pro2Gen then maps to genomic, as it does for cDNA after Coding2Pro.
     * @param groupedInputs
     */
    public void map(Map<VariantType, List<VariantInput>> groupedInputs) {
        if (groupedInputs.containsKey(VariantType.VARIANT_ID)) {
            List<VariantInput> idInputs = groupedInputs.get(VariantType.VARIANT_ID);
            Map<VariantFormat, List<VariantInput>> idGroups = idInputs.stream().collect(Collectors.groupingBy(VariantInput::getFormat));

            dbsnpLookup(idGroups.get(VariantFormat.DBSNP));
            clinvarLookup(idGroups.get(VariantFormat.CLINVAR));
            cosmicLookup(idGroups.get(VariantFormat.COSMIC));
            uniprotVarLookup(idGroups.get(VariantFormat.UNIPROT_VAR));
        }
    }

    public void dbsnpLookup(List<VariantInput> dbsnpIdInputs) {
        if (dbsnpIdInputs == null || dbsnpIdInputs.isEmpty())
            return;

        Set<String> dbsnpIds = dbsnpIdInputs.stream().map(VariantInput::getInputStr).collect(Collectors.toSet());

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
                        input.addMessage(MessageCode.PV402_DBSNP_NOT_MAPPED);
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
            clinvarRCVMap = clinVarRepo.getByRCVMap(clinvarIdTypeMap.get(ClinvarParser.RCV).stream()
                    .map(Id2Gen::clinvarKey).distinct().collect(Collectors.toList()));
        }

        if (clinvarIdTypeMap.get(ClinvarParser.VCV) != null) {
            clinvarVCVMap = clinVarRepo.getByVCVMap(clinvarIdTypeMap.get(ClinvarParser.VCV).stream()
                    .map(Id2Gen::clinvarKey).distinct().collect(Collectors.toList()));
        }

        for (VariantInput input : clinvarIdInputs) {
            String id = clinvarKey(input);
            if (input.getIdPrefix().equals(ClinvarParser.RCV) && clinvarRCVMap != null) {
                addClinvarDerivedGenomicVariants(clinvarRCVMap.get(id), input);
            } else if (input.getIdPrefix().equals(ClinvarParser.VCV) && clinvarVCVMap != null) {
                addClinvarDerivedGenomicVariants(clinvarVCVMap.get(id), input);
            }
            if (input.getDerivedGenomicVariants().isEmpty()) {
                input.addMessage(MessageCode.PV404_CLINVAR_NOT_MAPPED);
            }
        }
    }

    public void cosmicLookup(List<VariantInput> cosmicIdInputs) {
        if (cosmicIdInputs == null || cosmicIdInputs.isEmpty())
            return;

        Map<String, List<VariantInput>> cosmicIdTypeMap = cosmicIdInputs.stream().collect(Collectors.groupingBy(VariantInput::getIdPrefix));

        Set<String> ids = cosmicIdTypeMap.get(CosmicParser.COSV) == null ? null :
                cosmicIdTypeMap.get(CosmicParser.COSV).stream().map(Id2Gen::upperKey).collect(Collectors.toSet());
        Set<String> legacyIds = Stream.concat(cosmicIdTypeMap.get(CosmicParser.COSM) == null ? Stream.empty() : cosmicIdTypeMap.get(CosmicParser.COSM).stream(),
                        cosmicIdTypeMap.get(CosmicParser.COSN) == null ? Stream.empty() : cosmicIdTypeMap.get(CosmicParser.COSN).stream())
                .map(Id2Gen::upperKey).collect(Collectors.toSet());

        Map<String, List<Cosmic>> cosmicIdMap = cosmicRepo.getById(ids).stream().collect(Collectors.groupingBy(Cosmic::getId));
        Map<String, List<Cosmic>> cosmicLegacyIdMap = cosmicRepo.getByLegacyId(legacyIds).stream().collect(Collectors.groupingBy(Cosmic::getLegacyId));

        cosmicIdInputs.stream().forEach(input -> {
            String id = upperKey(input);
            if (cosmicIdMap != null && input.getIdPrefix().equals(CosmicParser.COSV)) {
                addCosmicDerivedGenomicVariants(cosmicIdMap.get(id), input);
            } else if (cosmicLegacyIdMap != null && (input.getIdPrefix().equals(CosmicParser.COSM) || input.getIdPrefix().equals(CosmicParser.COSN))) {
                addCosmicDerivedGenomicVariants(cosmicLegacyIdMap.get(id), input);
            }
            if (input.getDerivedGenomicVariants().isEmpty()) {
                input.addMessage(MessageCode.PV406_COSMIC_NOT_MAPPED);
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

    /** UniProt VAR_ IDs (humsavar) → protein change on the ProteinInput; mapped to genomic by Pro2Gen. */
    public void uniprotVarLookup(List<VariantInput> varIdInputs) {
        if (varIdInputs == null || varIdInputs.isEmpty())
            return;
        Map<String, Humsavar> byId = humsavarRepo.getByVarIds(varIdInputs.stream()
                        .map(Id2Gen::upperKey).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Humsavar::varId, Function.identity(), (a, b) -> a));

        for (VariantInput i : varIdInputs) {
            ProteinInput input = (ProteinInput) i;
            String id = upperKey(input);
            Humsavar h = byId.get(id);
            if (h == null) {
                input.addMessage(MessageCode.PV408_UNIPROT_VAR_NOT_FOUND, id);
                continue;
            }
            input.setAccession(h.accession());
            input.setPosition(h.position());
            input.setRefAA(h.wtAa());
            input.setAltAA(h.mtAa());
        }
    }

    // IDs are matched upper case (the parsers accept any case); ClinVar also without its version (.N)
    static String upperKey(VariantInput input) {
        return input.getInputStr().trim().toUpperCase();
    }

    static String clinvarKey(VariantInput input) {
        return ClinvarParser.stripVersion(upperKey(input));
    }

}
