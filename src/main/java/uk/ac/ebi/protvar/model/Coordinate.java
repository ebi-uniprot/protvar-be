package uk.ac.ebi.protvar.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Coordinate {
    public String chr;
    public Integer pos;
    public String base;

    public Coordinate(String chr) {
        this.chr = chr;
    }

    public static Coordinate parseInputLine(String input) {
        String[] cols = input.split("\\s+");
        if (cols.length == 2) {
            try {
                Coordinate coordinate = new Coordinate(cols[0]);
                coordinate.setPos(Integer.parseInt(cols[1]));
                return coordinate;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
