package ezbuild.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("reviews")
@CompoundIndex(name = "product_user", def = "{'productId': 1, 'userId': 1}", unique = true)
@Getter
@Setter
public class Review {

    @Id
    private String id;

    private String productId;

    private String userId;

    private String username;

    @Min(value = 1, message = "Rating must be 1 to 5.")
    @Max(value = 5, message = "Rating must be 1 to 5.")
    private int rating = 5;

    @NotBlank(message = "Give your review a title.")
    @Size(max = 80)
    private String title;

    @Size(max = 1000, message = "Keep the review under 1000 characters.")
    private String comment;

    private boolean verifiedPurchase;

    @CreatedDate
    private Instant createdAt;
}
