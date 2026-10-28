package uk.ac.ebi.protvar.repo;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.types.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodingVariantRepoTest {

    private final CodingVariantRepo repo = new CodingVariantRepo(null);

    private List<String> filters(MappingRequest r, MapSqlParameterSource p) {
        return repo.filters(r, p);
    }

    @Test
    void noFilters() {
        assertTrue(filters(new MappingRequest(), new MapSqlParameterSource()).isEmpty());
    }

    @Test
    void flagFiltersAreColumns() {
        MappingRequest r = new MappingRequest();
        r.setKnown(true); r.setPocket(true); r.setInteract(true); r.setExperimentalModel(true);
        r.setDiseaseAssociation(true); r.setPtm(true); r.setMutagen(true); r.setDomain(true);
        r.setBinding(true); r.setActsite(true); r.setTransmem(true);
        assertEquals(List.of("known", "exp_structure", "pocket", "interface", "disease", "ptm", "mutagen", "domain",
                "binding", "actsite", "transmem"), filters(r, new MapSqlParameterSource()));
    }

    @Test
    void falseFlagsIgnored() {
        MappingRequest r = new MappingRequest();
        r.setKnown(false); r.setPocket(false);
        assertTrue(filters(r, new MapSqlParameterSource()).isEmpty());
    }

    @Test
    void caddBandsAreOrOfHalfOpenRanges() {
        MappingRequest r = new MappingRequest();
        r.setCadd(List.of(CaddCategory.PROBABLY_DELETERIOUS, CaddCategory.HIGHLY_LIKELY_DELETERIOUS));
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertEquals(List.of("((cadd >= :caddMin0 AND cadd < :caddMax0) OR (cadd >= :caddMin1 AND cadd < :caddMax1))"),
                filters(r, p));
        assertEquals(25.0, p.getValue("caddMin0"));
        assertEquals(30.0, p.getValue("caddMax0"));
        assertEquals(30.0, p.getValue("caddMin1"));
    }

    @Test
    void popeveOpenEndedBands() {
        MappingRequest r = new MappingRequest();
        r.setPopeve(List.of(PopEveClass.SEVERE, PopEveClass.UNLIKELY_DELETERIOUS));
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertEquals(List.of("((popeve < :popeveMax0) OR (popeve >= :popeveMin1))"), filters(r, p));
        assertEquals(-5.056, p.getValue("popeveMax0"));
        assertEquals(-4.617, p.getValue("popeveMin1"));
    }

    @Test
    void amClassesAsArray() {
        MappingRequest r = new MappingRequest();
        r.setAm(List.of(AmClass.PATHOGENIC, AmClass.AMBIGUOUS));
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertEquals(List.of("am_class = ANY(:amClasses)"), filters(r, p));
        assertArrayEquals(new Integer[]{1, -1}, (Integer[]) p.getValue("amClasses"));
    }

    @Test
    void rangesAndAlleleFreq() {
        MappingRequest r = new MappingRequest();
        r.setAviMin(20.0); r.setEsm1bMin(-12.0); r.setEsm1bMax(-3.0);
        r.setConservationMin(0.8);
        r.setAlleleFreq(List.of(AlleleFreqCategory.RARE));
        assertEquals(List.of("avi >= :aviMin", "esm1b >= :esm1bMin", "esm1b <= :esm1bMax",
                "conservation >= :conservationMin", "((af >= :afMin0 AND af < :afMax0))"),
                filters(r, new MapSqlParameterSource()));
    }

    @Test
    void stabilityUsesMaxForLikelyMinForUnlikely() {
        MappingRequest r = new MappingRequest();
        r.setStability(List.of(StabilityChange.LIKELY_DESTABILISING));
        assertEquals(List.of("foldx_ddg_max >= 2"), filters(r, new MapSqlParameterSource()));
        r.setStability(List.of(StabilityChange.UNLIKELY_DESTABILISING));
        assertEquals(List.of("foldx_ddg_min < 2"), filters(r, new MapSqlParameterSource()));
        r.setStability(List.of(StabilityChange.LIKELY_DESTABILISING, StabilityChange.UNLIKELY_DESTABILISING));
        assertEquals(List.of("foldx_ddg_max IS NOT NULL"), filters(r, new MapSqlParameterSource()));
    }
}
