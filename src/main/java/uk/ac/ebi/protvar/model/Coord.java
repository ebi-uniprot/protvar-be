package uk.ac.ebi.protvar.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class Coord {

    @AllArgsConstructor
    @Getter
    public static class Gen {

        String chr;
        Integer pos;

        public Object[] toObjectArray() {
            return new Object[] {chr, pos};
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof Gen)
            {
                Gen gen = (Gen) obj;
                if(this.chr.equals(gen.chr) && this.pos.equals(gen.pos))
                    return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.chr.hashCode() + this.pos.hashCode());
        }

    }

    @AllArgsConstructor
    public static class Prot {
        String acc;
        Integer pos;
        public Object[] toObjectArray() {
            return new Object[] {acc, pos};
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof Prot)
            {
                Prot prot = (Prot) obj;
                if(this.acc.equals(prot.acc) && this.pos.equals(prot.pos))
                    return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.acc.hashCode() + this.pos.hashCode());
        }
    }
}
