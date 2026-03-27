package papervalidation.domain.searchplan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchPlanResult {
    private double totalPOS;
    private List<SruResult> sruResults;
    private List<SearchArea> areas;
    private String method;
    private String description;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SruResult {
        private String sruId;
        private String sruName;
        private double areaNm2;
        private double poc;
        private double coverageFactor;
        private double pod;
        private double pos;
    }
}
