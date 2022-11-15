package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.grc.Coordinate;
import uk.ac.ebi.protvar.model.grc.Crossmap;
import uk.ac.ebi.protvar.model.response.AssemblyMapping;
import uk.ac.ebi.protvar.model.response.AssemblyMappingResponse;
import uk.ac.ebi.protvar.repo.VariantsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AssemblyMappingFetcher {

    private VariantsRepository variantRepository;


    public AssemblyMappingResponse getMappings(List<String> inputs, String from, String to) {

        List<AssemblyMapping> assemblyMappings = new ArrayList<>();
        List<Coordinate> fromCoordinates = new ArrayList<>();
        inputs.stream().map(String::trim)
                .forEach(input -> {
                    AssemblyMapping mapping = new AssemblyMapping();
                    assemblyMappings.add(mapping);
                    mapping.setInput(input);
                    Coordinate fromCoordinate = Coordinate.parseInputLine(input);
                    if (fromCoordinate == null) {
                        mapping.setError("Error parsing input");
                    } else {
                        mapping.setFrom(fromCoordinate);
                        fromCoordinates.add(fromCoordinate);
                    }
                });

        Map<String, List<Crossmap>> groupedCrossmaps = variantRepository.getCrossmaps(fromCoordinates.stream().map(Coordinate::getPos).collect(Collectors.toList()), from)
                .stream().collect(Collectors.groupingBy(Crossmap::getGroupByChrAnd37Pos));

        assemblyMappings.stream().filter(mapping -> mapping.getFrom() != null)
                .forEach(mapping -> {
                    String key = mapping.getFrom().getChr() + "-" + mapping.getFrom().getPos();
                    List<Crossmap> crossmap = groupedCrossmaps.get(key);
                    if (crossmap == null) {
                        mapping.setError("No mapping found");
                    } else {
                        if (crossmap.size() == 1) {
                            Coordinate toCoordinate = new Coordinate(crossmap.get(0).getChr());
                            if (from.equals(Assembly.GRCh37)) {
                                toCoordinate.setPos(crossmap.get(0).getGrch38Pos());
                                toCoordinate.setBase(crossmap.get(0).getGrch38Base());
                                mapping.getFrom().setBase(crossmap.get(0).getGrch37Base());
                            } else if (from.equals(Assembly.GRCh38)) {
                                toCoordinate.setPos(crossmap.get(0).getGrch37Pos());
                                toCoordinate.setBase(crossmap.get(0).getGrch37Base());
                                mapping.getFrom().setBase(crossmap.get(0).getGrch38Base());
                            }
                            mapping.setTo(toCoordinate);
                        } else {
                            mapping.setError("Multiple mappings found");
                        }
                    }
                });

        return new AssemblyMappingResponse(from, to, assemblyMappings);
    }
}

