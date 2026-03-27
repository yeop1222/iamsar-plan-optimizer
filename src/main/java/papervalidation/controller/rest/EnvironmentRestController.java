package papervalidation.controller.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import papervalidation.domain.sru.EnvironmentCondition;
import papervalidation.domain.sru.SearchCondition;
import papervalidation.domain.sru.SearchObjectCategory;
import papervalidation.domain.sru.SruType;
import papervalidation.service.sru.EnvironmentService;
import papervalidation.service.sru.SruService;
import papervalidation.service.sru.SweepWidthTable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EnvironmentRestController {

    private final EnvironmentService environmentService;
    private final SruService sruService;
    private final SweepWidthTable sweepWidthTable;

    public EnvironmentRestController(EnvironmentService environmentService,
                                      SruService sruService,
                                      SweepWidthTable sweepWidthTable) {
        this.environmentService = environmentService;
        this.sruService = sruService;
        this.sweepWidthTable = sweepWidthTable;
    }

    @GetMapping("/environment")
    public ResponseEntity<EnvironmentCondition> getEnvironment() {
        return ResponseEntity.ok(environmentService.getEnvironment());
    }

    @PostMapping("/environment")
    public ResponseEntity<EnvironmentCondition> updateEnvironment(@RequestBody EnvironmentCondition condition) {
        environmentService.updateEnvironment(condition);
        sruService.recalculateAll();
        return ResponseEntity.ok(condition);
    }

    @GetMapping("/search-condition")
    public ResponseEntity<SearchCondition> getSearchCondition() {
        return ResponseEntity.ok(environmentService.getSearchCondition());
    }

    @PostMapping("/search-condition")
    public ResponseEntity<SearchCondition> updateSearchCondition(@RequestBody SearchCondition condition) {
        environmentService.updateSearchCondition(condition);
        sruService.recalculateAll();
        return ResponseEntity.ok(condition);
    }

    /** Combined: get both environment + search condition */
    @GetMapping("/conditions")
    public ResponseEntity<Map<String, Object>> getConditions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("environment", environmentService.getEnvironment());
        result.put("searchCondition", environmentService.getSearchCondition());
        return ResponseEntity.ok(result);
    }

    /** Combined: update both at once */
    @PostMapping("/conditions")
    public ResponseEntity<Map<String, Object>> updateConditions(@RequestBody Map<String, Object> body) {
        // Environment
        if (body.containsKey("environment")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envMap = (Map<String, Object>) body.get("environment");
            EnvironmentCondition env = new EnvironmentCondition();
            if (envMap.containsKey("visibility")) env.setVisibility(((Number)envMap.get("visibility")).doubleValue());
            if (envMap.containsKey("windSpeed")) env.setWindSpeed(((Number)envMap.get("windSpeed")).doubleValue());
            if (envMap.containsKey("waveHeight")) env.setWaveHeight(((Number)envMap.get("waveHeight")).doubleValue());
            environmentService.updateEnvironment(env);
        }
        // Search condition
        if (body.containsKey("searchCondition")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> scMap = (Map<String, Object>) body.get("searchCondition");
            SearchCondition sc = new SearchCondition();
            if (scMap.containsKey("category")) sc.setCategory(SearchObjectCategory.valueOf((String)scMap.get("category")));
            if (scMap.containsKey("objectSize")) sc.setObjectSize(((Number)scMap.get("objectSize")).intValue());
            environmentService.updateSearchCondition(sc);
        }
        sruService.recalculateAll();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("environment", environmentService.getEnvironment());
        result.put("searchCondition", environmentService.getSearchCondition());
        result.put("sruList", sruService.getAll());
        return ResponseEntity.ok(result);
    }

    /** Available sizes for a given SRU type + category */
    @GetMapping("/sru/available-sizes")
    public ResponseEntity<List<Integer>> getAvailableSizes(
            @RequestParam String type,
            @RequestParam String category) {
        SruType sruType = SruType.valueOf(type);
        SearchObjectCategory cat = SearchObjectCategory.valueOf(category);
        return ResponseEntity.ok(sweepWidthTable.getAvailableSizes(sruType, cat));
    }
}
