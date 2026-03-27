package papervalidation.controller.rest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import papervalidation.domain.ga.GaConfig;
import papervalidation.domain.searchplan.SearchArea;
import papervalidation.domain.searchplan.SearchPlanResult;
import papervalidation.service.ga.GaOptimizer;
import papervalidation.service.searchplan.BaselinePlanService;
import papervalidation.service.searchplan.ManualPlanService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/search-plan")
public class SearchPlanRestController {

    private final ManualPlanService manualPlanService;
    private final GaOptimizer gaOptimizer;
    private final BaselinePlanService baselinePlanService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SearchPlanRestController(ManualPlanService manualPlanService,
                                     GaOptimizer gaOptimizer,
                                     BaselinePlanService baselinePlanService) {
        this.manualPlanService = manualPlanService;
        this.gaOptimizer = gaOptimizer;
        this.baselinePlanService = baselinePlanService;
    }

    @PostMapping("/manual/evaluate")
    public ResponseEntity<SearchPlanResult> manualEvaluate(@RequestBody Map<String, List<SearchArea>> request) {
        List<SearchArea> areas = request.get("areas");
        if (areas == null || areas.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(manualPlanService.evaluate(areas));
    }

    @PostMapping("/ga/optimize")
    public ResponseEntity<?> gaOptimize(@RequestBody Map<String, GaConfig> request) {
        GaConfig config = request.get("config");
        if (config == null) config = new GaConfig();

        String optId = gaOptimizer.createOptimizationId();

        final GaConfig finalConfig = config;
        executor.submit(() -> {
            try {
                SearchPlanResult result = gaOptimizer.optimize(finalConfig, (gen, data) -> {
                    SseEmitter emitter = emitters.get(optId);
                    if (emitter != null) {
                        try {
                            emitter.send(SseEmitter.event().name("progress").data(data));
                        } catch (IOException e) {
                            // Client disconnected
                        }
                    }
                });

                SseEmitter emitter = emitters.get(optId);
                if (emitter != null) {
                    try {
                        emitter.send(SseEmitter.event().name("complete").data(result));
                        emitter.complete();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            } catch (Exception e) {
                SseEmitter emitter = emitters.get(optId);
                if (emitter != null) {
                    emitter.completeWithError(e);
                }
            } finally {
                emitters.remove(optId);
            }
        });

        return ResponseEntity.ok(Map.of("optimizationId", optId));
    }

    @GetMapping(value = "/ga/optimize/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter gaStream(@RequestParam String id) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        return emitter;
    }

    @PostMapping("/baseline/generate")
    public ResponseEntity<SearchPlanResult> baselineGenerate() {
        return ResponseEntity.ok(baselinePlanService.generate());
    }
}
