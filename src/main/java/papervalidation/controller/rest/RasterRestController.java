package papervalidation.controller.rest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import papervalidation.domain.datum.DatumSet;
import papervalidation.domain.raster.PocRaster;
import papervalidation.service.datum.DatumService;
import papervalidation.service.raster.RasterService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/raster")
public class RasterRestController {

    private final DatumService datumService;
    private final RasterService rasterService;

    public RasterRestController(DatumService datumService, RasterService rasterService) {
        this.datumService = datumService;
        this.rasterService = rasterService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate() {
        DatumSet datumSet = datumService.get();
        if (datumSet == null || datumSet.getPoints() == null || datumSet.getPoints().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No datum set defined"));
        }

        PocRaster raster = rasterService.generate(datumSet);
        Map<String, Object> contour50 = rasterService.extractContour50(raster);

        Map<String, Object> rasterInfo = new LinkedHashMap<>();
        rasterInfo.put("rows", raster.getRows());
        rasterInfo.put("cols", raster.getCols());
        rasterInfo.put("cellSize", raster.getCellSize());
        rasterInfo.put("originX", raster.getOriginX());
        rasterInfo.put("originY", raster.getOriginY());
        rasterInfo.put("totalCells", raster.getRows() * raster.getCols());
        rasterInfo.put("activeCells", raster.getActiveCells());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rasterInfo", rasterInfo);
        result.put("extent", raster.getExtent());
        result.put("contour50", contour50);

        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@RequestParam(defaultValue = "0.6") double opacity) {
        PocRaster raster = rasterService.getCurrentRaster();
        if (raster == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] png = rasterService.generatePng(raster, opacity);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @GetMapping("/data")
    public ResponseEntity<?> data() {
        PocRaster raster = rasterService.getCurrentRaster();
        if (raster == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", raster.getRows());
        result.put("cols", raster.getCols());
        result.put("cellSize", raster.getCellSize());
        result.put("originX", raster.getOriginX());
        result.put("originY", raster.getOriginY());
        result.put("data", raster.getData());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/poc")
    public ResponseEntity<?> poc(@RequestParam double x, @RequestParam double y) {
        PocRaster raster = rasterService.getCurrentRaster();
        if (raster == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("poc", raster.getPocAt(x, y)));
    }
}
