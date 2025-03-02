package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
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

    @Column(name = "import_type", nullable = false) // Use importCategory to better differentiate from dataset type(?)
    private String importType; // e.g., "clinvar_import"

    @Column(name = "key_name", nullable = false)
    private String keyName;    // e.g., "clinvar_ids"

    @Column(name = "value", nullable = false)
    private Long value;        // e.g., 3960831

    @Column(name = "note", nullable = true)
    private String note;       // e.g., "Release v1.2.0 - Includes new mappings" or Table name used for import

    @Column(name = "datasetType", nullable = false)
    private String datasetType; // e.g., "core", "individual"

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdAt;

    public Stats(String importType, String keyName, Long value, String note, String datasetType) {
        this.importType = importType;
        this.keyName = keyName;
        this.value = value;
        this.note = note;
        this.datasetType = datasetType;
    }
}
