package papervalidation.util;

/**
 * EPSG:4326 (WGS84) <-> EPSG:3857 (Web Mercator) 좌표 변환 유틸리티.
 */
public final class CoordinateConverter {

    private static final double HALF_CIRCUMFERENCE = 20037508.34;

    private CoordinateConverter() {
    }

    /** WGS84 경도 -> Web Mercator X */
    public static double lonToX(double lon) {
        return lon * HALF_CIRCUMFERENCE / 180.0;
    }

    /** WGS84 위도 -> Web Mercator Y */
    public static double latToY(double lat) {
        double rad = Math.toRadians(lat);
        return Math.log(Math.tan(Math.PI / 4.0 + rad / 2.0)) * HALF_CIRCUMFERENCE / Math.PI;
    }

    /** Web Mercator X -> WGS84 경도 */
    public static double xToLon(double x) {
        return x * 180.0 / HALF_CIRCUMFERENCE;
    }

    /** Web Mercator Y -> WGS84 위도 */
    public static double yToLat(double y) {
        return Math.toDegrees(Math.atan(Math.exp(y * Math.PI / HALF_CIRCUMFERENCE)) * 2.0 - Math.PI / 2.0);
    }

    /** WGS84 [lon, lat] -> Web Mercator [x, y] */
    public static double[] toMercator(double lon, double lat) {
        return new double[]{lonToX(lon), latToY(lat)};
    }

    /** Web Mercator [x, y] -> WGS84 [lon, lat] */
    public static double[] toWgs84(double x, double y) {
        return new double[]{xToLon(x), yToLat(y)};
    }

    /** Web Mercator 축척 보정 계수: 1 / cos(lat) */
    public static double mercatorScaleFactor(double latDeg) {
        return 1.0 / Math.cos(Math.toRadians(latDeg));
    }
}