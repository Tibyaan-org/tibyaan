package example.support.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled), BI-2. An Offer in the support bounded context, sharing only its simple name
 * with the crm Offer (Volume 7 section 12.6).
 */
@Entity
public class Offer {

    @Id
    private Long id;

    private String reason;
}
