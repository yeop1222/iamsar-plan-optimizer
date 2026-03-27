package papervalidation.controller.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import papervalidation.domain.datum.DatumSet;
import papervalidation.service.datum.DatumService;
import papervalidation.service.raster.RasterService;

@RestController
@RequestMapping("/api/datum")
public class DatumRestController {

    private final DatumService datumService;
    private final RasterService rasterService;

    public DatumRestController(DatumService datumService, RasterService rasterService) {
        this.datumService = datumService;
        this.rasterService = rasterService;
    }

    @PostMapping
    public ResponseEntity<DatumSet> save(@RequestBody DatumSet datumSet) {
        return ResponseEntity.ok(datumService.save(datumSet));
    }

    @GetMapping
    public ResponseEntity<DatumSet> get() {
        DatumSet ds = datumService.get();
        return ds != null ? ResponseEntity.ok(ds) : ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        datumService.clear();
        rasterService.clear();
        return ResponseEntity.ok().build();
    }
}
