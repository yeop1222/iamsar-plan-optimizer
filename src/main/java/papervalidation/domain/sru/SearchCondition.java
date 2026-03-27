package papervalidation.domain.sru;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Global search object condition (same target for all SRUs).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchCondition {
    private SearchObjectCategory category = SearchObjectCategory.PIW;
    private int objectSize = 1;
}
