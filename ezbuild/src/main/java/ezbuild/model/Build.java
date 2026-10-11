package ezbuild.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("builds")
@CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
@Getter
@Setter
public class Build {

    @Id
    private String id;

    private String userId;

    private String name;

    private BuildSource source = BuildSource.MANUAL;

    /** Short public share code, e.g. /b/xk29ab */
    @Indexed(unique = true, sparse = true)
    private String shareCode;

    /** Snapshot of the parts at save time. */
    private List<BuildPart> parts = new ArrayList<>();

    private long total;

    private String note;

    @CreatedDate
    private Instant createdAt;

    @Getter
    @Setter
    public static class BuildPart {
        private ProductCategory category;
        private String productId;
        private String nameSnap;
        private long priceSnap;
        private int qty = 1;
    }
}
