package uk.ac.ebi.protvar.processor;

import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.processor.CsvHeaders.Column;
import uk.ac.ebi.protvar.processor.CsvHeaders.Group;
import uk.ac.ebi.protvar.utils.Constants;

import java.util.*;

/**
 * One download's columns: Variant and Mapping, plus the requested groups. With {@code tableColumns} (the result
 * table's shown columns), Variant and Mapping keep only the columns the table shows, plus the input line and notes.
 */
public record CsvLayout(Set<Group> groups, Set<String> tableColumns) {

    public static CsvLayout of(DownloadRequest request) {
        Set<Group> groups = EnumSet.of(Group.VARIANT, Group.MAPPING);
        if (Boolean.TRUE.equals(request.getFunction())) groups.add(Group.FUNCTION);
        if (Boolean.TRUE.equals(request.getPrediction())) groups.add(Group.PREDICTION);
        if (Boolean.TRUE.equals(request.getPopulation())) groups.add(Group.POPULATION);
        if (Boolean.TRUE.equals(request.getStructure())) groups.add(Group.STRUCTURE);
        List<String> cols = request.getColumns();
        return new CsvLayout(groups, cols == null || cols.isEmpty() ? null : Set.copyOf(cols));
    }

    public boolean has(Group group) {
        return groups.contains(group);
    }

    public boolean keeps(Column c) {
        if (!groups.contains(c.group())) return false;
        boolean trimmable = c.group() == Group.VARIANT || c.group() == Group.MAPPING;
        if (!trimmable || tableColumns == null || CsvHeaders.ALWAYS.equals(c.table())) return true;
        return c.table() != null && tableColumns.contains(c.table());
    }

    public boolean keeps(String name) {
        return CsvHeaders.COLUMNS.stream().anyMatch(c -> c.name().equals(name) && keeps(c));
    }

    public String[] header() {
        return CsvHeaders.COLUMNS.stream().filter(this::keeps).map(Column::name).toArray(String[]::new);
    }

    /**
     * The row for the kept columns. {@code values}: each group's values in its full column order; a group that is
     * missing (or not requested) is N/A.
     */
    public String[] row(Map<Group, List<String>> values) {
        List<String> row = new ArrayList<>();
        Map<Group, Integer> next = new EnumMap<>(Group.class);
        for (Column c : CsvHeaders.COLUMNS) {
            int i = next.merge(c.group(), 1, Integer::sum) - 1;
            if (!keeps(c)) continue;
            List<String> v = values.get(c.group());
            String val = v == null || i >= v.size() ? null : v.get(i);
            row.add(val == null || val.isBlank() ? Constants.NA : val);
        }
        return row.toArray(String[]::new);
    }
}
