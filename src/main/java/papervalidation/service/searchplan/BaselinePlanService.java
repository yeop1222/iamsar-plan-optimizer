package papervalidation.service.searchplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import papervalidation.domain.datum.DatumPoint;
import papervalidation.domain.datum.DatumSet;
import papervalidation.domain.datum.DatumType;
import papervalidation.domain.raster.PocRaster;
import papervalidation.domain.searchplan.SearchArea;
import papervalidation.domain.searchplan.SearchPlanResult;
import papervalidation.domain.sru.Sru;
import papervalidation.domain.sru.SruType;
import papervalidation.service.datum.DatumService;
import papervalidation.service.raster.RasterService;
import papervalidation.service.sru.SruService;
import papervalidation.util.CoordinateConverter;
import papervalidation.util.VincentyUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * IAMSAR Baseline placement (L-7 worksheet).
 *
 * Follows the IamsarSearchPlanServiceImpl algorithm:
 *   Step 1: Zta = Σ(W_corr_i × V_i × T_i)
 *   Step 2: Ro = fs × E  (fs from Zr = Zta / E²)
 *   Step 3: Ao — single-point: 4Ro², double-point: 4Ro² + 2Ro×dd
 *   Step 4: Co = Zta / Ao  (same C for all SRUs)
 *   Step 5: S_i = W_corr_i / Co, subArea_i = V_i × T_i × S_i
 *   Step 6: Strip tiling, priority-sorted (ROTARY > FIXED > SHIP)
 *   Step 7: Rotation from datum geometry
 *
 * Only POINT datum is supported. LINE/AREA returns error.
 */
@Service
public class BaselinePlanService {

    private static final Logger log = LoggerFactory.getLogger(BaselinePlanService.class);

    private final RasterService rasterService;
    private final SruService sruService;
    private final DatumService datumService;
    private final PosCalculator posCalculator;

    public BaselinePlanService(RasterService rasterService, SruService sruService,
                                DatumService datumService, PosCalculator posCalculator) {
        this.rasterService = rasterService;
        this.sruService = sruService;
        this.datumService = datumService;
        this.posCalculator = posCalculator;
    }

    public SearchPlanResult generate() {
        PocRaster raster = rasterService.getCurrentRaster();
        if (raster == null) throw new IllegalStateException("No POC raster generated");

        DatumSet datumSet = datumService.get();
        if (datumSet == null) throw new IllegalStateException("No datum set defined");
        if (datumSet.getType() != DatumType.POINT) {
            throw new IllegalStateException("IAMSAR Baseline only supports POINT datum");
        }
        if (datumSet.getPoints() == null || datumSet.getPoints().isEmpty()) {
            throw new IllegalStateException("No datum points defined");
        }

        List<Sru> srus = sruService.getAll();
        if (srus.isEmpty()) throw new IllegalStateException("No SRUs registered");

        // ──────────────────────────────────────────────
        // Step 1: Total search effort Zta (NM²)
        // ──────────────────────────────────────────────
        double zta = 0;
        for (Sru sru : srus) {
            zta += sru.getEffectiveSweepWidth() * sru.getSearchSpeed() * sru.getEndurance();
        }
        log.debug("Baseline Step1: Zta = {} NM²", zta);

        // ──────────────────────────────────────────────
        // Step 2: Optimal radius Ro from error E
        // ──────────────────────────────────────────────
        double E = datumSet.getPoints().stream()
                .mapToDouble(DatumPoint::getErrorNm)
                .average()
                .orElse(3.0);

        double fz = E * E;                        // effort factor
        double Zr = zta / fz;                     // relative effort (dimensionless)
        double fs = 0.7179 * Math.pow(Zr, 0.2570); // search factor (normal conditions)
        double Ro = fs * E;                        // optimal radius (NM)
        log.debug("Baseline Step2: E={} NM, fz={}, Zr={}, fs={}, Ro={} NM", E, fz, Zr, fs, Ro);

        // ──────────────────────────────────────────────
        // Step 3: Optimal area Ao and shape
        // ──────────────────────────────────────────────
        List<DatumPoint> points = datumSet.getPoints();
        double widthNm;   // cross-track (short side)
        double lengthNm;  // along-track (long side)
        double rotationDeg = 0;
        double dd = 0;    // separation distance between datum points (NM)

        // POC weighted centroid (for positioning)
        double sumX = 0, sumY = 0, sumP = 0;
        for (int r = 0; r < raster.getRows(); r++) {
            for (int c = 0; c < raster.getCols(); c++) {
                double p = raster.getData()[r][c];
                if (p > 0) {
                    sumX += raster.getCellCenterX(c) * p;
                    sumY += raster.getCellCenterY(r) * p;
                    sumP += p;
                }
            }
        }
        double centroidX = sumX / sumP;
        double centroidY = sumY / sumP;
        double centroidLat = CoordinateConverter.yToLat(centroidY);

        if (points.size() == 1) {
            // Single-point: square
            widthNm = 2.0 * Ro;
            lengthNm = 2.0 * Ro;
            rotationDeg = 0;
            log.debug("Baseline Step3 (single-point): width={} NM, length={} NM", widthNm, lengthNm);
        } else {
            // Multi-point: compute separation distance between the two most distant points
            double maxDist = 0;
            int idxA = 0, idxB = 1;
            for (int i = 0; i < points.size(); i++) {
                for (int j = i + 1; j < points.size(); j++) {
                    double dist = VincentyUtils.distance(
                            points.get(i).getLatitude(), points.get(i).getLongitude(),
                            points.get(j).getLatitude(), points.get(j).getLongitude());
                    if (dist > maxDist) {
                        maxDist = dist;
                        idxA = i;
                        idxB = j;
                    }
                }
            }
            dd = maxDist / 1852.0; // meters → NM

            widthNm = 2.0 * Ro;
            lengthNm = 2.0 * Ro + dd;

            // Rotation: direction from point A to point B
            DatumPoint pA = points.get(idxA);
            DatumPoint pB = points.get(idxB);
            double[] mcA = CoordinateConverter.toMercator(pA.getLongitude(), pA.getLatitude());
            double[] mcB = CoordinateConverter.toMercator(pB.getLongitude(), pB.getLatitude());
            rotationDeg = Math.toDegrees(Math.atan2(mcB[1] - mcA[1], mcB[0] - mcA[0]));

            log.debug("Baseline Step3 (multi-point): dd={} NM, width={} NM, length={} NM, rotation={}°",
                    dd, widthNm, lengthNm, rotationDeg);
        }

        double Ao = widthNm * lengthNm; // optimal area (NM²)

        // ──────────────────────────────────────────────
        // Step 4: Optimal Coverage Factor Co (same for all SRUs)
        // ──────────────────────────────────────────────
        double Co = (Ao > 0) ? zta / Ao : 1.0;
        log.debug("Baseline Step4: Ao={} NM², Co={}", Ao, Co);

        // ──────────────────────────────────────────────
        // Step 5: Per-SRU track spacing and sub-area
        // ──────────────────────────────────────────────
        // Sort by priority then reorder center-out (highest priority → center)
        List<Sru> prioritySorted = new ArrayList<>(srus);
        prioritySorted.sort(Comparator.comparingInt(s -> sruPriority(s.getType())));
        List<Sru> orderedSrus = centerOutReorder(prioritySorted);

        int n = orderedSrus.size();
        double[] subLengthNm = new double[n]; // each strip's length along the length axis
        for (int i = 0; i < n; i++) {
            Sru sru = orderedSrus.get(i);
            double Si = sru.getEffectiveSweepWidth() / Co; // track spacing (NM)
            double subAreaNm2 = sru.getSearchSpeed() * sru.getEndurance() * Si;
            subLengthNm[i] = (widthNm > 0) ? subAreaNm2 / widthNm : 0;

            log.debug("Baseline Step5 SRU[{}]: W_corr={}, S={} NM, subArea={} NM², subLength={} NM",
                    sru.getName(), sru.getEffectiveSweepWidth(), Si, subAreaNm2, subLengthNm[i]);
        }

        // ──────────────────────────────────────────────
        // Step 6: Strip tiling (same as original AISAR)
        //
        // Geometry:
        //   lengthBearing = datum direction (rotationDeg) — along-track
        //   widthBearing  = lengthBearing - 90° — cross-track
        //   width  = 2*Ro (cross-track, shared by all SRUs)
        //   length = 2*Ro + dd (along-track, varies per SRU as subLength)
        //
        // Algorithm:
        //   1. Find q1 = top-left corner of the ENTIRE search rectangle
        //      centered on the datum centroid
        //   2. Stack strips sequentially along the length axis from q1
        //   3. Each strip: width × subLength[i]
        //   4. The entire block is centered because q1 is computed from centroid
        // ──────────────────────────────────────────────
        // ──────────────────────────────────────────────
        // Step 6: Sequential stack → then shift entire block to centroid
        //
        // lengthBearing = datum direction (along-track)
        // widthBearing  = lengthBearing - 90 (cross-track)
        //
        // 1. Stack strips sequentially along lengthBearing from origin (0,0)
        // 2. Compute the center of the stacked block
        // 3. Shift all centers so block center = POC centroid
        // ──────────────────────────────────────────────
        double lbRad = Math.toRadians(rotationDeg);       // length bearing
        double wbRad = Math.toRadians(rotationDeg - 90);  // width bearing

        double lDx = Math.cos(lbRad), lDy = Math.sin(lbRad);
        double wDx = Math.cos(wbRad), wDy = Math.sin(wbRad);

        double stripWidM = widthNm * 1852.0; // all strips share the same width (cross-track)

        // Phase 1: Stack from (0,0) along length axis, record each strip center
        double[][] tempCenters = new double[n][2];
        double cursor = 0; // accumulated offset along length axis (meters)

        for (int i = 0; i < n; i++) {
            double stripLenM = subLengthNm[i] * 1852.0;
            // Strip center relative to (0,0): mid of strip along length axis, centered on width
            double midAlongLength = cursor + stripLenM / 2.0;
            tempCenters[i][0] = lDx * midAlongLength;
            tempCenters[i][1] = lDy * midAlongLength;
            cursor += stripLenM;
        }

        // Phase 2: Compute block center = average of all strip centers (or simply mid of cursor)
        double blockCenterX = lDx * (cursor / 2.0);
        double blockCenterY = lDy * (cursor / 2.0);

        // Phase 3: Shift so block center maps to POC centroid
        double shiftX = centroidX - blockCenterX;
        double shiftY = centroidY - blockCenterY;

        List<SearchArea> areas = new ArrayList<>();
        List<SearchPlanResult.SruResult> sruResults = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Sru sru = orderedSrus.get(i);
            double stripLenM = subLengthNm[i] * 1852.0;

            double cx = tempCenters[i][0] + shiftX;
            double cy = tempCenters[i][1] + shiftY;

            // SearchArea: width = cross-track (widthNm), height = along-track (subLength)
            // width = along-track (length direction, aligned with rotation), height = cross-track
            SearchArea sa = new SearchArea(sru.getId(), cx, cy, stripLenM, stripWidM, rotationDeg);
            areas.add(sa);

            double poc = posCalculator.sumPocInRect(raster, sa);
            double areaNm2 = posCalculator.calculateAreaNm2(sa, centroidLat);
            double C = posCalculator.calculateCoverageFactor(sru, areaNm2);
            double pod = posCalculator.calculatePod(C);
            double pos = poc * pod;

            sruResults.add(new SearchPlanResult.SruResult(
                    sru.getId(), sru.getName(), areaNm2, poc, C, pod, pos));
        }

        double totalPOS = posCalculator.calculateTotalPos(raster, areas, orderedSrus);

        SearchPlanResult result = new SearchPlanResult();
        result.setTotalPOS(totalPOS);
        result.setSruResults(sruResults);
        result.setAreas(areas);
        result.setMethod("IAMSAR_BASELINE");
        result.setDescription(String.format(
                "L-7: E=%.1f NM, Ro=%.2f NM, Ao=%.1f NM², Co=%.3f, dd=%.1f NM",
                E, Ro, Ao, Co, dd));
        return result;
    }

    /**
     * Reorder priority-sorted list to center-out order.
     * Matches original AISAR sortSearchResource():
     *   Even indices (0,2,4,...) from the end, then odd indices (1,3,5,...) from the start.
     *   Result: highest priority ends up in the center of the stacked strips.
     */
    private <T> List<T> centerOutReorder(List<T> prioritySorted) {
        int size = prioritySorted.size();
        if (size <= 1) return new ArrayList<>(prioritySorted);

        List<T> result = new ArrayList<>();
        int lastEven = (size % 2 == 0) ? (size - 2) : (size - 1);
        int evensCount = (size + 1) / 2;

        for (int i = 0; i < size; i++) {
            int srcIdx = (i < evensCount)
                    ? (lastEven - 2 * i)
                    : (1 + 2 * (i - evensCount));
            result.add(prioritySorted.get(srcIdx));
        }
        return result;
    }

    /**
     * SRU priority for center placement: lower value = higher priority = placed closer to center.
     * ROTARY(0) > FIXED(1) > SHIP(2)
     */
    private int sruPriority(SruType type) {
        switch (type) {
            case ROTARY: return 0;
            case FIXED:  return 1;
            case LARGE:  return 2;
            case MEDIUM: return 3;
            case SMALL:  return 4;
            case BOAT:   return 5;
            default:     return 6;
        }
    }
}
