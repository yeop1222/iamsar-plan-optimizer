package papervalidation.domain.raster;

import lombok.Data;

/**
 * POC raster data: 2D grid of probability values in EPSG:3857 coordinates.
 */
@Data
public class PocRaster {
    private int rows;
    private int cols;
    private double cellSize;     // meters (200)
    private double originX;      // EPSG:3857 min X
    private double originY;      // EPSG:3857 min Y
    private double[][] data;     // [row][col] POC values, normalized to sum=1.0
    private int activeCells;     // cells with POC > 0

    public double getMaxX() {
        return originX + cols * cellSize;
    }

    public double getMaxY() {
        return originY + rows * cellSize;
    }

    public double[] getExtent() {
        return new double[]{originX, originY, getMaxX(), getMaxY()};
    }

    /**
     * Get POC value at given EPSG:3857 coordinate.
     * Returns 0 if out of bounds.
     */
    public double getPocAt(double x, double y) {
        int col = (int) ((x - originX) / cellSize);
        int row = (int) ((y - originY) / cellSize);
        if (row < 0 || row >= rows || col < 0 || col >= cols) return 0.0;
        return data[row][col];
    }

    /**
     * Get cell center coordinates (EPSG:3857) for given row/col.
     */
    public double getCellCenterX(int col) {
        return originX + (col + 0.5) * cellSize;
    }

    public double getCellCenterY(int row) {
        return originY + (row + 0.5) * cellSize;
    }
}
