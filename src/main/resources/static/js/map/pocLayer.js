/**
 * POC heatmap layer: PNG overlay + 50% contour with toggle.
 */

let pocImageLayer = null;
let contourSource = null;
let contourLayer = null;

export function initPocLayer(map) {
    contourSource = new ol.source.Vector();
    contourLayer = new ol.layer.Vector({
        source: contourSource,
        style: new ol.style.Style({
            stroke: new ol.style.Stroke({
                color: '#FFD700',
                width: 2,
                lineDash: [8, 4]
            }),
            fill: new ol.style.Fill({
                color: 'rgba(255, 215, 0, 0.05)'
            })
        }),
        zIndex: 11
    });
    map.addLayer(contourLayer);

    // 50% contour toggle
    var toggle = document.getElementById('contourToggle');
    if (toggle) {
        toggle.addEventListener('change', function() {
            contourLayer.setVisible(this.checked);
        });
    }
}

export function showPocHeatmap(map, extent) {
    removePocHeatmap(map);

    pocImageLayer = new ol.layer.Image({
        source: new ol.source.ImageStatic({
            url: '/api/raster/image?t=' + Date.now(),
            imageExtent: extent,
            projection: 'EPSG:3857'
        }),
        opacity: 0.6,
        zIndex: 10
    });
    map.addLayer(pocImageLayer);

    // Show opacity control
    var ctrl = document.getElementById('opacityControl');
    if (ctrl) ctrl.style.display = 'flex';
}

export function removePocHeatmap(map) {
    if (pocImageLayer) {
        map.removeLayer(pocImageLayer);
        pocImageLayer = null;
    }
}

export function showContour50(geojson) {
    if (!contourSource) return;
    contourSource.clear();
    if (!geojson || !geojson.geometry) return;

    var format = new ol.format.GeoJSON();
    var features = format.readFeatures(geojson, {
        dataProjection: 'EPSG:3857',
        featureProjection: 'EPSG:3857'
    });
    contourSource.addFeatures(features);

    // Respect current toggle state
    var toggle = document.getElementById('contourToggle');
    if (toggle) {
        contourLayer.setVisible(toggle.checked);
    }
}

export function clearContour() {
    if (contourSource) contourSource.clear();
}

export function setPocOpacity(opacity) {
    if (pocImageLayer) {
        pocImageLayer.setOpacity(opacity);
    }
}

export function getPocImageLayer() {
    return pocImageLayer;
}

export function setContourVisible(visible) {
    if (contourLayer) {
        contourLayer.setVisible(visible);
    }
}
