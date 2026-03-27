package papervalidation.service.raster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import papervalidation.domain.datum.DatumSet;
import papervalidation.domain.datum.DatumType;
import papervalidation.domain.raster.PocRaster;
import papervalidation.domain.raster.SamplePoint;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import papervalidation.domain.datum.DatumType;

import java.util.*;
import java.util.List;

/**
 * POC raster generation service.
 * Generates a 200m-resolution grid of POC values using Gaussian kernels.
 */
@Service
public class RasterService {

    private static final Logger log = LoggerFactory.getLogger(RasterService.class);
    private static final double CELL_SIZE = 200.0;  // meters
    private static final int LUT_SIZE = 1001;
    private static final double MAX_Z = 3.0;        // 3σ cutoff

    private volatile PocRaster currentRaster;
    private volatile byte[] currentImage;

    // Gaussian LUT
    private final double[] gaussianLut;

    public RasterService() {
        gaussianLut = new double[LUT_SIZE];
        for (int i = 0; i < LUT_SIZE; i++) {
            double z = MAX_Z * i / (LUT_SIZE - 1.0);
            gaussianLut[i] = Math.exp(-z * z / 2.0);
        }
    }

    public PocRaster generate(DatumSet datumSet) {
        log.info("Generating POC raster for {} datum with {} points",
                datumSet.getType(), datumSet.getPoints().size());

        // 1. Select sampling strategy
        SamplingStrategy strategy = selectStrategy(datumSet.getType());
        List<SamplePoint> samples = strategy.generateSamples(datumSet);

        if (samples.isEmpty()) {
            throw new IllegalStateException("No sample points generated");
        }

        log.debug("Generated {} sample points", samples.size());

        // 2. Calculate bounding box + 3σ padding
        double maxSigma = samples.stream().mapToDouble(SamplePoint::getSigma).max().orElse(1000);
        double padding = maxSigma * MAX_Z;

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (SamplePoint sp : samples) {
            minX = Math.min(minX, sp.getX());
            maxX = Math.max(maxX, sp.getX());
            minY = Math.min(minY, sp.getY());
            maxY = Math.max(maxY, sp.getY());
        }
        double originX = minX - padding;
        double originY = minY - padding;
        double endX = maxX + padding;
        double endY = maxY + padding;

        // 3. Create grid
        int cols = (int) Math.ceil((endX - originX) / CELL_SIZE);
        int rows = (int) Math.ceil((endY - originY) / CELL_SIZE);

        log.debug("Raster grid: {}x{}, origin=({}, {})", rows, cols, originX, originY);

        double[][] raw = new double[rows][cols];

        // 4. Accumulate Gaussian contributions
        for (SamplePoint sp : samples) {
            double sigma = sp.getSigma();
            double weight = sp.getWeight();
            double norm = weight / (2.0 * Math.PI * sigma * sigma);
            double cutoff = sigma * MAX_Z;

            int colMin = Math.max(0, (int) ((sp.getX() - cutoff - originX) / CELL_SIZE));
            int colMax = Math.min(cols - 1, (int) ((sp.getX() + cutoff - originX) / CELL_SIZE));
            int rowMin = Math.max(0, (int) ((sp.getY() - cutoff - originY) / CELL_SIZE));
            int rowMax = Math.min(rows - 1, (int) ((sp.getY() + cutoff - originY) / CELL_SIZE));

            for (int r = rowMin; r <= rowMax; r++) {
                double cy = originY + (r + 0.5) * CELL_SIZE;
                double dy = cy - sp.getY();
                for (int c = colMin; c <= colMax; c++) {
                    double cx = originX + (c + 0.5) * CELL_SIZE;
                    double dx = cx - sp.getX();
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    double z = dist / sigma;
                    if (z > MAX_Z) continue;
                    double g = lookupGaussian(z);
                    raw[r][c] += norm * g;
                }
            }
        }

        // 5. Normalize
        double total = 0;
        int activeCells = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                total += raw[r][c];
                if (raw[r][c] > 0) activeCells++;
            }
        }

        if (total > 0) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    raw[r][c] /= total;
                }
            }
        }

        // 6. Build PocRaster
        PocRaster raster = new PocRaster();
        raster.setRows(rows);
        raster.setCols(cols);
        raster.setCellSize(CELL_SIZE);
        raster.setOriginX(originX);
        raster.setOriginY(originY);
        raster.setData(raw);
        raster.setActiveCells(activeCells);

        // 7. Generate PNG image
        currentRaster = raster;
        currentImage = generatePng(raster, 0.6);

        log.info("POC raster generated: {}x{}, {} active cells", rows, cols, activeCells);
        return raster;
    }

    /**
     * Extract 50% probability region contour as GeoJSON polygon (EPSG:3857).
     */
    public Map<String, Object> extractContour50(PocRaster raster) {
        double[][] data = raster.getData();
        int rows = raster.getRows();
        int cols = raster.getCols();

        // Sort POC values descending to find threshold
        List<Double> values = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (data[r][c] > 0) values.add(data[r][c]);
            }
        }
        values.sort(Collections.reverseOrder());

        double cumSum = 0;
        double threshold = 0;
        for (double v : values) {
            cumSum += v;
            if (cumSum >= 0.5) {
                threshold = v;
                break;
            }
        }

        // Create binary mask and extract boundary cells
        List<double[]> boundaryCells = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (data[r][c] >= threshold) {
                    // Check if boundary cell
                    if (isBoundary(data, r, c, rows, cols, threshold)) {
                        boundaryCells.add(new double[]{
                                raster.getCellCenterX(c),
                                raster.getCellCenterY(r)
                        });
                    }
                }
            }
        }

        if (boundaryCells.isEmpty()) return null;

        // Create convex hull as simplified contour
        List<double[]> sorted = sortByAngle(boundaryCells);

        // Build GeoJSON
        List<List<Double>> coords = new ArrayList<>();
        for (double[] pt : sorted) {
            coords.add(Arrays.asList(pt[0], pt[1]));
        }
        if (!sorted.isEmpty()) {
            coords.add(Arrays.asList(sorted.get(0)[0], sorted.get(0)[1]));
        }

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Polygon");
        geometry.put("coordinates", Collections.singletonList(coords));

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", Collections.emptyMap());

        return feature;
    }

    private boolean isBoundary(double[][] data, int r, int c, int rows, int cols, double threshold) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols || data[nr][nc] < threshold) {
                return true;
            }
        }
        return false;
    }

    private List<double[]> sortByAngle(List<double[]> points) {
        double cx = 0, cy = 0;
        for (double[] p : points) { cx += p[0]; cy += p[1]; }
        cx /= points.size();
        cy /= points.size();
        final double fcx = cx, fcy = cy;

        // Convex hull (Graham scan)
        points.sort((a, b) -> {
            double angleA = Math.atan2(a[1] - fcy, a[0] - fcx);
            double angleB = Math.atan2(b[1] - fcy, b[0] - fcx);
            return Double.compare(angleA, angleB);
        });

        // Simplify: take every Nth point if too many
        if (points.size() > 200) {
            int step = points.size() / 100;
            List<double[]> simplified = new ArrayList<>();
            for (int i = 0; i < points.size(); i += step) {
                simplified.add(points.get(i));
            }
            return simplified;
        }
        return points;
    }

    /**
     * Generate PNG image from raster with jet colormap.
     */
    public byte[] generatePng(PocRaster raster, double opacity) {
        int rows = raster.getRows();
        int cols = raster.getCols();
        double[][] data = raster.getData();

        // Find max POC for normalization
        double maxPoc = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                maxPoc = Math.max(maxPoc, data[r][c]);
            }
        }

        BufferedImage img = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_ARGB);
        int alpha = (int) (opacity * 255);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double v = data[r][c];
                if (v <= 0) {
                    img.setRGB(c, rows - 1 - r, 0x00000000); // transparent
                } else {
                    double t = (maxPoc > 0) ? v / maxPoc : 0;
                    Color jet = jetColor(t);
                    int argb = (alpha << 24) | (jet.getRed() << 16) | (jet.getGreen() << 8) | jet.getBlue();
                    img.setRGB(c, rows - 1 - r, argb);
                }
            }
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PNG", e);
        }
    }

    /**
     * Jet colormap: blue -> cyan -> green -> yellow -> red
     */
    private Color jetColor(double t) {
        double r = clamp(1.5 - Math.abs(t - 0.75) * 4, 0, 1);
        double g = clamp(1.5 - Math.abs(t - 0.5) * 4, 0, 1);
        double b = clamp(1.5 - Math.abs(t - 0.25) * 4, 0, 1);
        return new Color((int) (r * 255), (int) (g * 255), (int) (b * 255));
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double lookupGaussian(double z) {
        if (z >= MAX_Z) return 0;
        int idx = (int) (z / MAX_Z * (LUT_SIZE - 1));
        idx = Math.min(idx, LUT_SIZE - 1);
        return gaussianLut[idx];
    }

    private SamplingStrategy selectStrategy(DatumType type) {
        switch (type) {
            case LINE: return new LineSamplingStrategy();
            case AREA: return new AreaSamplingStrategy();
            default:   return new PointSamplingStrategy();
        }
    }

    public PocRaster getCurrentRaster() {
        return currentRaster;
    }

    public byte[] getCurrentImage() {
        return currentImage;
    }

    public void clear() {
        currentRaster = null;
        currentImage = null;
    }
}
