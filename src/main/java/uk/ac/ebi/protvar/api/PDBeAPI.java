package uk.ac.ebi.protvar.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import uk.ac.ebi.protvar.model.response.Structure;
import uk.ac.ebi.protvar.model.response.StructureResidue;
import uk.ac.ebi.protvar.service.StructureService;

import java.util.ArrayList;
import java.util.List;

@Service
public class PDBeAPI {
    private static final Logger logger = LoggerFactory.getLogger(PDBeAPI.class);

    @Autowired
    @Qualifier("pdbeRestTemplate")
    private RestTemplate pdbeRestTemplate;

    public List<StructureResidue> get(String accession, int position) {
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.pdbeRestTemplate.getUriTemplateHandler();
        UriBuilder uriBuilder = handler.builder().path(accession).path("/").path(String.valueOf(position)).path("/")
                .path(String.valueOf(++position));
        List<StructureResidue> residues = new ArrayList<>();

        try {
            ResponseEntity<Structure[]> response = this.pdbeRestTemplate.getForEntity(uriBuilder.build(), Structure[].class);
            if (response.getBody() != null) {
                Structure[] structures = response.getBody();
                for (Structure str : structures) {
                    residues.add(StructureService.toStructureResidue(str, position));
                }
            }
            return residues;
        } catch (Exception ex) {
            logger.error("Exception calling PDBe API for accession {}, position {}", accession, position);
            return null;
        }
    }
}
