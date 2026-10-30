package uk.ac.ebi.protvar.repo;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.types.CaddCategory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UploadVariantRepoTest {

    private final UploadVariantRepo repo = new UploadVariantRepo(null, new CodingVariantRepo(null));

    private MappingRequest request() {
        MappingRequest r = new MappingRequest();
        r.setKnown(true); r.setPocket(true);
        r.setCadd(List.of(CaddCategory.HIGHLY_LIKELY_DELETERIOUS));
        return r;
    }

    @Test
    void codingVariantBuildsFilterOnCopiedColumns() {
        ReflectionTestUtils.setField(repo, "fromCodingVariant", true);
        MapSqlParameterSource p = new MapSqlParameterSource();
        assertEquals("""
                WHERE uv.upload_key = :k
                  AND known
                  AND ((cadd >= CAST(:caddMin0 AS real) AND cadd < CAST(:caddMax0 AS real)))
                  AND pocket""", repo.where(request(), p));
        assertEquals(30.0, p.getValue("caddMin0"));
    }

    @Test
    void oldBuildsKeepExists() {
        ReflectionTestUtils.setField(repo, "dbsnpTable", "dbsnp");
        ReflectionTestUtils.setField(repo, "genomicScoreTable", "gs");
        ReflectionTestUtils.setField(repo, "pocketTable", "pocket");
        String where = repo.where(request(), new MapSqlParameterSource());
        assertTrue(where.contains("EXISTS (SELECT 1 FROM dbsnp d"));
        assertTrue(where.contains("EXISTS (SELECT 1 FROM pocket p"));
    }

    @Test
    void notesFilterConditionsOnTheLine() {
        MappingRequest r = new MappingRequest();
        r.setNotes("any");
        r.setCodes(List.of("PV-110", "PV-103"));
        MapSqlParameterSource p = new MapSqlParameterSource();
        String c = repo.lineConditions(r, p, "l.idx");
        assertTrue(c.contains("AND EXISTS (SELECT 1 FROM protvar_runtime.upload_line ul WHERE ul.upload_key = :k AND ul.input_idx = l.idx)"));
        assertTrue(c.contains("AND ul.codes && :codes::text[]"));
        assertArrayEquals(new String[]{"PV-110", "PV-103"}, (String[]) p.getValue("codes"));
        assertTrue(UploadVariantRepo.filtersOrSorts(r));

        r = new MappingRequest();
        r.setNotes("none");
        assertTrue(repo.lineConditions(r, new MapSqlParameterSource(), "uv.input_idx").contains("AND NOT EXISTS ("));
        assertEquals("", repo.lineConditions(new MappingRequest(), new MapSqlParameterSource(), "l.idx"));
    }
}
