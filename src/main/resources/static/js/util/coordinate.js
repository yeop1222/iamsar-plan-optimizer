/**
 * Coordinate conversion utilities (EPSG:4326 <-> EPSG:3857).
 * Uses proj4js + OpenLayers for transformations.
 */

const HALF_CIRCUMFERENCE = 20037508.34;

export function lonToX(lon) {
    return lon * HALF_CIRCUMFERENCE / 180.0;
}

export function latToY(lat) {
    const rad = lat * Math.PI / 180.0;
    return Math.log(Math.tan(Math.PI / 4.0 + rad / 2.0)) * HALF_CIRCUMFERENCE / Math.PI;
}

export function xToLon(x) {
    return x * 180.0 / HALF_CIRCUMFERENCE;
}

export function yToLat(y) {
    return (Math.atan(Math.exp(y * Math.PI / HALF_CIRCUMFERENCE)) * 2.0 - Math.PI / 2.0) * 180.0 / Math.PI;
}

export function toMercator(lon, lat) {
    return [lonToX(lon), latToY(lat)];
}

export function toWgs84(x, y) {
    return [xToLon(x), yToLat(y)];
}

export function mercatorScaleFactor(latDeg) {
    return 1.0 / Math.cos(latDeg * Math.PI / 180.0);
}