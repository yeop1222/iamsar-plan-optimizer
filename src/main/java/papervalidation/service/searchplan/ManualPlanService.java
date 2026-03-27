package papervalidation.service.searchplan;

import org.springframework.stereotype.Service;
import papervalidation.domain.raster.PocRaster;
import papervalidation.domain.searchplan.SearchArea;
import papervalidation.domain.searchplan.SearchPlanResult;
import papervalidation.domain.sru.Sru;
import papervalidation.service.raster.RasterService;
import papervalidation.service.sru.SruService;
import papervalidation.util.CoordinateConverter;

import java.util.ArrayList;
import java.util.List;

@Service
public class ManualPlanService {

    private final RasterService rasterService;
    private final SruService sruService;
    private final PosCalculator posCalculator;

    public ManualPlanService(RasterService rasterService, SruService sruService, PosCalculator posCalculator) {
        this.rasterService = rasterService;
        this.sruService = sruService;
        this.posCalculator = posCalculator;
    }

    public SearchPlanResult evaluate(List<SearchArea> areas) {
        PocRaster raster = rasterService.getCurrentRaster();
        if (raster == null) {
            throw new IllegalStateException("No POC raster generated yet");
        }

        List<Sru> allSrus = sruService.getAll();
        List<SearchPlanResult.SruResult> sruResults = new ArrayList<>();

        for (SearchArea area : areas) {
            Sru sru = sruService.getById(area.getSruId());
            if (sru == null) continue;

            double poc = posCalculator.sumPocInRect(raster, area);
            double centerLat = CoordinateConverter.yToLat(area.getCenterY());
            double areaNm2 = posCalculator.calculateAreaNm2(area, centerLat);
            double C = posCalculator.calculateCoverageFactor(sru, areaNm2);
            double pod = posCalculator.calculatePod(C);
            double pos = poc * pod;

            sruResults.add(new SearchPlanResult.SruResult(
                    sru.getId(), sru.getName(), areaNm2, poc, C, pod, pos));
        }

        double totalPOS = posCalculator.calculateTotalPos(raster, areas, allSrus);

        SearchPlanResult result = new SearchPlanResult();
        result.setTotalPOS(totalPOS);
        result.setSruResults(sruResults);
        result.setAreas(areas);
        result.setMethod("MANUAL");
        return result;
    }
}
