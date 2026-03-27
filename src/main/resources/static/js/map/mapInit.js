/**
 * OpenLayers map initialization.
 */

let map = null;

export function initMap() {
    map = new ol.Map({
        target: 'mapContainer',
        view: new ol.View({
            projection: 'EPSG:3857',
            center: ol.proj.transform([128.5, 35.5], 'EPSG:4326', 'EPSG:3857'),
            zoom: 9,
            maxZoom: 18
        }),
        controls: ol.control.defaults.defaults({ zoom: true, rotate: false })
    });

    // OSM base layer
    const osmLayer = new ol.layer.Tile({
        source: new ol.source.OSM()
    });
    map.addLayer(osmLayer);

    return map;
}

export function getMap() {
    return map;
}