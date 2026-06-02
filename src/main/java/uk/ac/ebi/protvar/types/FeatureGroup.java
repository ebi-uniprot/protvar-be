package uk.ac.ebi.protvar.types;

import lombok.Getter;
import uk.ac.ebi.protvar.common.model.FeatureType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Groups of UniProt feature types used by the advanced-search filters
 * (mutagenesis / PTM / domain / binding site / active site). The FE sends
 * a boolean per group on MappingRequest; the BE expands the selected
 * group(s) into a flat list of {@link FeatureType} names and filters
 * against function_feature.type via SQL.
 *
 * Several groups happen to equal a UniProt FeatureCategory exactly (PTM,
 * MUTAGENESIS); others are sub-splits of a single category (DOMAIN,
 * BINDING_SITE, ACTIVE_SITE all live under DOMAINS_AND_SITES). The BE
 * filters by `type IN (...)` uniformly — one code path, type-level
 * granularity, indexed on function_feature(type).
 */
@Getter
public enum FeatureGroup {

    PTM("Post-translational modifications",
            FeatureType.MOD_RES,
            FeatureType.LIPID,
            FeatureType.CARBOHYD,
            FeatureType.DISULFID,
            FeatureType.CROSSLNK),

    MUTAGENESIS("Experimentally altered sites",
            FeatureType.MUTAGEN),

    DOMAIN("Protein domains, regions and notable sites",
            FeatureType.DOMAIN,
            FeatureType.REPEAT,
            FeatureType.ZN_FING,
            FeatureType.DNA_BIND,
            FeatureType.REGION,
            FeatureType.COILED,
            FeatureType.MOTIF,
            FeatureType.SITE),

    BINDING_SITE("Binding sites",
            FeatureType.BINDING),

    ACTIVE_SITE("Active sites",
            FeatureType.ACT_SITE),

    TRANSMEMBRANE("Transmembrane regions",
            FeatureType.TRANSMEM);

    private final String description;
    private final List<FeatureType> featureTypes;

    FeatureGroup(String description, FeatureType... types) {
        this.description = description;
        this.featureTypes = Arrays.asList(types);
    }

    /** Returns the feature type names (e.g. "MOD_RES", "LIPID") in this group. */
    public List<String> getFeatureTypeNames() {
        return featureTypes.stream()
                .map(FeatureType::name)
                .collect(Collectors.toList());
    }

    /** Returns the group containing the given feature type, or null if unmapped. */
    public static FeatureGroup fromFeatureType(FeatureType featureType) {
        if (featureType == null) return null;
        return Arrays.stream(values())
                .filter(group -> group.featureTypes.contains(featureType))
                .findFirst()
                .orElse(null);
    }

    /** Same as {@link #fromFeatureType} but accepts the type name or value as a string. */
    public static FeatureGroup fromFeatureTypeString(String featureTypeStr) {
        return fromFeatureType(FeatureType.fromString(featureTypeStr));
    }
}
