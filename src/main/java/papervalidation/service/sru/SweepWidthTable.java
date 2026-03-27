package papervalidation.service.sru;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import papervalidation.domain.sru.SearchObjectCategory;
import papervalidation.domain.sru.SruType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static papervalidation.domain.sru.SearchObjectCategory.*;

/**
 * IAMSAR sweep width tables — loaded from CSV resources.
 *
 * Resources:
 *   data/uncorrected_width.csv      → W_0(sruType, altitude, visibility, category, size)
 *   data/speed_correction_factor.csv → f_v(sruType, speed, category, size)
 *
 * Interpolation: linear on visibility, altitude(aircraft), speed, size.
 * f_w: conditional formula based on wind/wave/object.
 */
@Component
public class SweepWidthTable {

    private static final Logger log = LoggerFactory.getLogger(SweepWidthTable.class);

    public static final int[] ALTITUDE_LEVELS = {150, 300, 600};

    // W_0: "TYPE:ALT:VIS:CAT:SIZE" → W_0
    private final Map<String, Double> w0Table = new HashMap<>(2000);
    private int[] visLevels;

    // f_v: "TYPE:SPEED:CAT:SIZE" → f_v
    private final Map<String, Double> fvTable = new HashMap<>(200);
    private final Map<SruType, int[]> speedLevels = new EnumMap<>(SruType.class);

    // Available sizes: "GROUP:CAT" → sorted list
    private final Map<String, List<Integer>> availableSizesMap = new LinkedHashMap<>();

    private static final Map<String, SearchObjectCategory> CAT_MAP = Map.of(
            "PIW", PIW, "RAFT", RAFT, "POWERBOAT", POWERBOAT, "SAILBOAT", SAILBOAT, "SHIP", SHIP
    );

    @PostConstruct
    public void init() {
        loadW0Csv();
        loadFvCsv();
        buildAvailableSizes();
        log.info("SweepWidthTable loaded: {} W_0 entries, {} f_v entries", w0Table.size(), fvTable.size());
    }

    // ─── Public API ────────────────────────────────────

    public double lookupW0(SruType sruType, int altitude, double visibility,
                           SearchObjectCategory category, int size) {
        return interpolateW0(sruType, altitude, visibility, category, size);
    }

    public double lookupVelocityFactor(SruType sruType, double speed,
                                        SearchObjectCategory category, int size) {
        if (sruType.isSurface()) return 1.0;
        return interpolateFv(sruType, speed, category, size);
    }

    public double weatherFactor(SearchObjectCategory category, int objectSize,
                                double windSpeed, double waveHeight) {
        boolean isLargeVessel =
                ((category == POWERBOAT || category == SAILBOAT) && objectSize >= 10)
                || category == SHIP;

        if (windSpeed > 25 || waveHeight > 1.5) {
            return isLargeVessel ? 0.9 : 0.25;
        }
        if (windSpeed > 15 || waveHeight > 1.0) {
            return isLargeVessel ? 0.9 : 0.5;
        }
        return 1.0;
    }

    public List<Integer> getAvailableSizes(SruType sruType, SearchObjectCategory category) {
        String group = sruTypeGroup(sruType);
        String key = group + ":" + category.name();
        return availableSizesMap.getOrDefault(key, List.of(1));
    }

    // ─── CSV Loading ───────────────────────────────────

    private void loadW0Csv() {
        Set<Integer> visSet = new TreeSet<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/uncorrected_width.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 6) continue;
                String sruTypeStr = parts[0].trim().toUpperCase();
                int alt = Integer.parseInt(parts[1].trim());
                int vis = Integer.parseInt(parts[2].trim());
                String catStr = parts[3].trim().toUpperCase();
                int size = Integer.parseInt(parts[4].trim());
                double w0 = Double.parseDouble(parts[5].trim());

                visSet.add(vis);
                SearchObjectCategory cat = CAT_MAP.get(catStr);
                if (cat == null) continue;
                try {
                    SruType type = SruType.valueOf(sruTypeStr);
                    w0Table.put(type.name() + ":" + alt + ":" + vis + ":" + cat.name() + ":" + size, w0);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            log.error("Failed to load uncorrected_width.csv", e);
        }
        visLevels = visSet.stream().mapToInt(Integer::intValue).toArray();
    }

    private void loadFvCsv() {
        Map<SruType, Set<Integer>> speedSets = new EnumMap<>(SruType.class);
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/speed_correction_factor.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 5) continue;
                String sruTypeStr = parts[0].trim().toUpperCase();
                int speed = (int) Double.parseDouble(parts[1].trim());
                String catStr = parts[2].trim().toUpperCase();
                int size = Integer.parseInt(parts[3].trim());
                double fv = Double.parseDouble(parts[4].trim());

                SearchObjectCategory cat = CAT_MAP.get(catStr);
                if (cat == null) continue;
                try {
                    SruType type = SruType.valueOf(sruTypeStr);
                    fvTable.put(type.name() + ":" + speed + ":" + cat.name() + ":" + size, fv);
                    speedSets.computeIfAbsent(type, k -> new TreeSet<>()).add(speed);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            log.error("Failed to load speed_correction_factor.csv", e);
        }
        for (var entry : speedSets.entrySet()) {
            speedLevels.put(entry.getKey(), entry.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private void buildAvailableSizes() {
        Map<String, Set<Integer>> temp = new LinkedHashMap<>();
        for (String key : w0Table.keySet()) {
            String[] parts = key.split(":");
            SruType type = SruType.valueOf(parts[0]);
            String cat = parts[3];
            int size = Integer.parseInt(parts[4]);
            String mapKey = sruTypeGroup(type) + ":" + cat;
            temp.computeIfAbsent(mapKey, k -> new TreeSet<>()).add(size);
        }
        for (var entry : temp.entrySet()) {
            availableSizesMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    // ─── W_0 Interpolation ─────────────────────────────

    private double interpolateW0(SruType type, int altitude, double vis,
                                  SearchObjectCategory cat, int size) {
        List<Integer> sizes = getAvailableSizes(type, cat);
        if (sizes.isEmpty()) return 0;
        int[] sb = findBounds(sizes, size);
        double wLo = interpolateW0_visAlt(type, altitude, vis, cat, sb[0]);
        if (sb[0] == sb[1]) return wLo;
        double wHi = interpolateW0_visAlt(type, altitude, vis, cat, sb[1]);
        return wLo + (double)(size - sb[0]) / (sb[1] - sb[0]) * (wHi - wLo);
    }

    private double interpolateW0_visAlt(SruType type, int altitude, double vis,
                                         SearchObjectCategory cat, int size) {
        if (type.isAircraft()) {
            int[] ab = findBounds(ALTITUDE_LEVELS, altitude);
            double vLo = interpolateW0_vis(type, ab[0], vis, cat, size);
            if (ab[0] == ab[1]) return vLo;
            double vHi = interpolateW0_vis(type, ab[1], vis, cat, size);
            return vLo + (double)(altitude - ab[0]) / (ab[1] - ab[0]) * (vHi - vLo);
        }
        return interpolateW0_vis(type, 0, vis, cat, size);
    }

    private double interpolateW0_vis(SruType type, int alt, double vis,
                                      SearchObjectCategory cat, int size) {
        if (visLevels == null || visLevels.length == 0) return 0;
        int[] vb = findBoundsArr(visLevels, vis);
        double wLo = w0Table.getOrDefault(type.name()+":"+alt+":"+vb[0]+":"+cat.name()+":"+size, 0.0);
        if (vb[0] == vb[1]) return wLo;
        double wHi = w0Table.getOrDefault(type.name()+":"+alt+":"+vb[1]+":"+cat.name()+":"+size, 0.0);
        return wLo + (vis - vb[0]) / (vb[1] - vb[0]) * (wHi - wLo);
    }

    // ─── f_v Interpolation ─────────────────────────────

    private double interpolateFv(SruType type, double speed,
                                  SearchObjectCategory cat, int size) {
        int[] speeds = speedLevels.get(type);
        if (speeds == null || speeds.length == 0) return 1.0;
        int[] sb = findBoundsArr(speeds, speed);
        double fLo = getFv(type, sb[0], cat, size);
        if (sb[0] == sb[1]) return fLo;
        double fHi = getFv(type, sb[1], cat, size);
        return fLo + (speed - sb[0]) / (sb[1] - sb[0]) * (fHi - fLo);
    }

    private double getFv(SruType type, int speed, SearchObjectCategory cat, int size) {
        String key = type.name() + ":" + speed + ":" + cat.name() + ":" + size;
        Double v = fvTable.get(key);
        if (v != null) return v;
        List<Integer> sizes = getAvailableSizes(type, cat);
        if (sizes.isEmpty()) return 1.0;
        int nearest = sizes.stream().min(Comparator.comparingInt(s -> Math.abs(s - size))).orElse(size);
        return fvTable.getOrDefault(type.name()+":"+speed+":"+cat.name()+":"+nearest, 1.0);
    }

    // ─── Utility ───────────────────────────────────────

    private String sruTypeGroup(SruType type) {
        switch (type) {
            case LARGE: case MEDIUM: return "LARGE_MED";
            case SMALL: case BOAT:   return "SMALL_BOAT";
            case ROTARY:             return "ROTARY";
            case FIXED:              return "FIXED";
            default:                 return "LARGE_MED";
        }
    }

    private int[] findBounds(List<Integer> sorted, int val) {
        if (sorted.isEmpty()) return new int[]{0, 0};
        if (val <= sorted.get(0)) return new int[]{sorted.get(0), sorted.get(0)};
        if (val >= sorted.get(sorted.size()-1)) return new int[]{sorted.get(sorted.size()-1), sorted.get(sorted.size()-1)};
        for (int i = 0; i < sorted.size()-1; i++) {
            if (val >= sorted.get(i) && val <= sorted.get(i+1))
                return new int[]{sorted.get(i), sorted.get(i+1)};
        }
        return new int[]{sorted.get(0), sorted.get(0)};
    }

    private int[] findBounds(int[] arr, int val) {
        return findBounds(Arrays.stream(arr).boxed().collect(Collectors.toList()), val);
    }

    private int[] findBoundsArr(int[] arr, double val) {
        if (arr.length == 0) return new int[]{0, 0};
        if (val <= arr[0]) return new int[]{arr[0], arr[0]};
        if (val >= arr[arr.length-1]) return new int[]{arr[arr.length-1], arr[arr.length-1]};
        for (int i = 0; i < arr.length-1; i++) {
            if (val >= arr[i] && val <= arr[i+1]) return new int[]{arr[i], arr[i+1]};
        }
        return new int[]{arr[0], arr[0]};
    }
}
