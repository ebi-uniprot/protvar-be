package uk.ac.ebi.protvar.fetcher;

import com.google.common.collect.Iterables;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.parser.InputParser;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.service.UserInputCacheService;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@AllArgsConstructor
public class UserInputHandler implements InputHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserInputHandler.class);
    private static final int USER_INPUT_CHUNK_SIZE = 2000;
    private UserInputCacheService userInputCacheService;

    // TODO advanced filter not taken into account

    // For UI/API
    public Page<UserInput> pagedInput(MappingRequest request) {
        List<String> fullList = userInputCacheService.getInputs(request.getInput());
        if (fullList == null || fullList.isEmpty()) return Page.empty();

        int page = request.getPage() - 1; // request page is 1-based, pageable is 0-based
        int pageSize = request.getPageSize();
        int total = fullList.size();

        int fromIndex = page * pageSize;
        if (fromIndex >= total) return Page.empty();

        List<String> subList = fullList.subList(fromIndex, Math.min(fromIndex + pageSize, total));
        List<UserInput> parsed = InputParser.parse(subList);
        Pageable pageable = PageRequest.of(page, pageSize);
        return new PageImpl<>(parsed, pageable, total);
    }

    // For download
    // todo check for possible use of jdbcTemplate queryForStream
    public Stream<List<UserInput>> streamChunkedInput(MappingRequest request) {
        List<String> fullInput = userInputCacheService.getInputs(request.getInput());

        if (fullInput == null || fullInput.isEmpty()) {
            return Stream.empty();
        }
        if (fullInput.size() > 1_000_000) {
            LOGGER.warn("Cached input size is very large: {}", fullInput.size());
        }

        // Use Guava's Iterables.partition or implement own partition logic
        return StreamSupport.stream(
                Iterables.partition(fullInput, USER_INPUT_CHUNK_SIZE).spliterator(), false
        ).map(InputParser::parse);
    }
}
