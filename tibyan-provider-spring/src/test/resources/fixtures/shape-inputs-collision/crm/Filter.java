package example.crm.shape;

import lombok.Data;

/** SI-10: a DTO whose simple name is shared with the support context's. */
@Data
public class Filter {
    private String query;
}
