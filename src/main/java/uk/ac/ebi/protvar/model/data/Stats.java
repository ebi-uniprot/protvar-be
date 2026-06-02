package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "stats")
@NoArgsConstructor
@Getter
@Setter
public class Stats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "release", nullable = false)
    private String release; // e.g., "2025_01" (add: "pre_2025_01")

    @Column(name = "type", nullable = false) // Use importCategory to better differentiate from dataset type(?)
    private String type; // e.g., "mapping"

    @Column(name = "category", nullable = false)
    private String category; // e.g., "g2p"

    @Column(name = "key", nullable = false)
    private String key;    // e.g., "mapping_count"

    @Column(name = "value", nullable = false)
    private Long value;        // e.g., 3960831

    @Column(name = "note", nullable = true)
    private String note;       // e.g., Table name used for import

    @Column(name = "created", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date created;
}
