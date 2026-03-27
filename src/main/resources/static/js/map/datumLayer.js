/**
 * Datum layer: markers + error circles on the map.
 * Supports dragging points (with error circle sync) and tooltip.
 */

let datumSource = null;
let datumLayer = null;
let dragInteraction = null;
let tooltipOverlay = null;
let pointFeatures = [];
let circleFeatures = [];  // parallel to pointFeatures by datumIndex
let connectingFeature = null;  // LineString or Polygon connecting datum points
let pointerDragKey = null;

export function initDatumLayer(map) {
    datumSource = new ol.source.Vector();
    datumLayer = new ol.layer.Vector({
        source: datumSource,
        style: function(feature) {
            var featureType = feature.get('featureType');
            if (featureType === 'errorCircle') {
                return new ol.style.Style({
                    stroke: new ol.style.Stroke({ color: 'rgba(255,0,0,0.5)', width: 1 }),
                    fill: new ol.style.Fill({ color: 'rgba(255,0,0,0.1)' })
                });
            }
            if (featureType === 'datumLine') {
                return new ol.style.Style({
                    stroke: new ol.style.Stroke({ color: '#FF0000', width: 2 })
                });
            }
            if (featureType === 'datumPolygon') {
                return new ol.style.Style({
                    stroke: new ol.style.Stroke({ color: '#FF0000', width: 2 }),
                    fill: new ol.style.Fill({ color: 'rgba(255,0,0,0.15)' })
                });
            }
            return new ol.style.Style({
                image: new ol.style.Circle({
                    radius: 7,
                    fill: new ol.style.Fill({ color: '#FF0000' }),
                    stroke: new ol.style.Stroke({ color: '#FFFFFF', width: 2 })
                }),
                text: new ol.style.Text({
                    text: feature.get('pointLabel') || '',
                    offsetY: -16,
                    font: 'bold 12px sans-serif',
                    fill: new ol.style.Fill({ color: '#CC0000' }),
                    stroke: new ol.style.Stroke({ color: '#FFF', width: 3 })
                })
            });
        },
        zIndex: 20
    });
    map.addLayer(datumLayer);

    var tooltipEl = document.getElementById('datumDragTooltip');
    if (tooltipEl) {
        tooltipOverlay = new ol.Overlay({
            element: tooltipEl,
            positioning: 'bottom-center',
            offset: [0, -20],
            stopEvent: false
        });
        map.addOverlay(tooltipOverlay);
    }

    return { datumSource, datumLayer };
}

export function clearDatumLayer() {
    if (datumSource) datumSource.clear();
    pointFeatures = [];
    circleFeatures = [];
    connectingFeature = null;
}

export function renderDatumPoints(datumSet, map) {
    clearDatumLayer();
    if (!datumSet || !datumSet.points || datumSet.points.length === 0) return;

    var points = datumSet.points;
    pointFeatures = [];
    circleFeatures = [];

    points.forEach(function(pt, idx) {
        var coord = ol.proj.transform([pt.longitude, pt.latitude], 'EPSG:4326', 'EPSG:3857');

        var pointFeature = new ol.Feature({
            geometry: new ol.geom.Point(coord),
            featureType: 'datumPoint',
            datumIndex: idx,
            pointLabel: '#' + (idx + 1)
        });
        datumSource.addFeature(pointFeature);
        pointFeatures.push(pointFeature);

        var radiusM = pt.errorNm * 1852;
        var latRad = pt.latitude * Math.PI / 180;
        var mercatorRadius = radiusM / Math.cos(latRad);
        var circleFeature = new ol.Feature({
            geometry: new ol.geom.Circle(coord, mercatorRadius),
            featureType: 'errorCircle',
            datumIndex: idx
        });
        datumSource.addFeature(circleFeature);
        circleFeatures.push(circleFeature);
    });

    connectingFeature = null;
    if (datumSet.type === 'LINE' && points.length >= 2) {
        var lineCoords = points.map(function(pt) {
            return ol.proj.transform([pt.longitude, pt.latitude], 'EPSG:4326', 'EPSG:3857');
        });
        connectingFeature = new ol.Feature({
            geometry: new ol.geom.LineString(lineCoords),
            featureType: 'datumLine'
        });
        datumSource.addFeature(connectingFeature);
    }

    if (datumSet.type === 'AREA' && points.length >= 3) {
        var polyCoords = points.map(function(pt) {
            return ol.proj.transform([pt.longitude, pt.latitude], 'EPSG:4326', 'EPSG:3857');
        });
        polyCoords.push(polyCoords[0]);
        connectingFeature = new ol.Feature({
            geometry: new ol.geom.Polygon([polyCoords]),
            featureType: 'datumPolygon'
        });
        datumSource.addFeature(connectingFeature);
    }

    var extent = datumSource.getExtent();
    if (extent && extent[0] !== Infinity) {
        var center = ol.extent.getCenter(extent);
        map.getView().animate({ center: center, duration: 300 });
    }
}

/**
 * Enable drag on datum point markers.
 * Error circles move with their point.
 * Tooltip shows distance/bearing to adjacent points only (idx-1, idx+1).
 */
export function enableDatumDrag(map, datumPoints, datumType) {
    var _datumType = datumType || 'POINT';
    disableDatumDrag(map);
    if (pointFeatures.length === 0) return;

    dragInteraction = new ol.interaction.Modify({
        features: new ol.Collection(pointFeatures),
        hitDetection: datumLayer,
        style: null
    });

    // Track which point is being dragged for circle sync
    var draggingIdx = -1;

    dragInteraction.on('modifystart', function(evt) {
        evt.features.forEach(function(f) {
            if (f.get('datumIndex') != null) draggingIdx = f.get('datumIndex');
        });
    });

    dragInteraction.on('modifyend', function(evt) {
        hideTooltip();
        evt.features.forEach(function(feature) {
            var idx = feature.get('datumIndex');
            if (idx == null) return;
            var coord = feature.getGeometry().getCoordinates();

            // Sync error circle position
            if (circleFeatures[idx]) {
                var circle = circleFeatures[idx].getGeometry();
                var radius = circle.getRadius();
                circleFeatures[idx].setGeometry(new ol.geom.Circle(coord, radius));
            }

            // Sync connecting line/polygon
            if (connectingFeature) {
                var geom = connectingFeature.getGeometry();
                var allCoords = pointFeatures.map(function(f) { return f.getGeometry().getCoordinates(); });
                if (geom.getType() === 'LineString') {
                    connectingFeature.setGeometry(new ol.geom.LineString(allCoords));
                } else if (geom.getType() === 'Polygon') {
                    var closed = allCoords.slice();
                    closed.push(closed[0]);
                    connectingFeature.setGeometry(new ol.geom.Polygon([closed]));
                }
            }

            var lonlat = ol.proj.transform(coord, 'EPSG:3857', 'EPSG:4326');
            document.dispatchEvent(new CustomEvent('datumPointMoved', {
                detail: { index: idx, latitude: parseFloat(lonlat[1].toFixed(6)), longitude: parseFloat(lonlat[0].toFixed(6)) }
            }));
        });
        draggingIdx = -1;
    });

    map.addInteraction(dragInteraction);

    // Pointer drag: sync circle in real-time + show tooltip for adjacent points only
    pointerDragKey = map.on('pointerdrag', function(evt) {
        if (draggingIdx < 0) return;

        var feature = pointFeatures[draggingIdx];
        if (!feature) return;
        var coord = feature.getGeometry().getCoordinates();

        // Real-time circle sync
        if (circleFeatures[draggingIdx]) {
            var circle = circleFeatures[draggingIdx].getGeometry();
            var radius = circle.getRadius();
            circleFeatures[draggingIdx].setGeometry(new ol.geom.Circle(coord, radius));
        }

        // Real-time connecting line/polygon sync
        if (connectingFeature) {
            var geom = connectingFeature.getGeometry();
            var allCoords = pointFeatures.map(function(f) { return f.getGeometry().getCoordinates(); });
            if (geom.getType() === 'LineString') {
                connectingFeature.setGeometry(new ol.geom.LineString(allCoords));
            } else if (geom.getType() === 'Polygon') {
                var closed = allCoords.slice();
                closed.push(closed[0]);
                connectingFeature.setGeometry(new ol.geom.Polygon([closed]));
            }
        }

        // Tooltip: only show for adjacent points (idx-1, idx+1)
        if (datumPoints.length >= 2) {
            showDragTooltip(draggingIdx, coord, datumPoints, evt.coordinate, _datumType);
        }
    });
}

export function disableDatumDrag(map) {
    if (dragInteraction) {
        map.removeInteraction(dragInteraction);
        dragInteraction = null;
    }
    if (pointerDragKey) {
        ol.Observable.unByKey(pointerDragKey);
        pointerDragKey = null;
    }
    hideTooltip();
}

function showDragTooltip(dragIdx, dragCoord3857, datumPoints, mapCoordinate, datumType) {
    var tooltipEl = document.getElementById('datumDragTooltip');
    if (!tooltipEl || !tooltipOverlay) return;

    var dragLonLat = ol.proj.transform(dragCoord3857, 'EPSG:3857', 'EPSG:4326');
    var dragLat = dragLonLat[1];
    var dragLon = dragLonLat[0];

    var lines = [];
    // Only adjacent: idx-1 and idx+1
    var neighbors = [];
    if (dragIdx > 0) neighbors.push(dragIdx - 1);
    if (dragIdx < datumPoints.length - 1) neighbors.push(dragIdx + 1);
    // For POINT with 2 points, the "other" point is always a neighbor
    if (neighbors.length === 0 && datumPoints.length === 2) {
        neighbors.push(dragIdx === 0 ? 1 : 0);
    }

    var dragPt = datumPoints[dragIdx];
    neighbors.forEach(function(i) {
        var pt = datumPoints[i];
        var distM = haversine(dragLat, dragLon, pt.latitude, pt.longitude);
        var distNm = distM / 1852;
        var brg = bearingCalc(dragLat, dragLon, pt.latitude, pt.longitude);
        var avgE = ((dragPt ? dragPt.errorNm : 3.5) + pt.errorNm) / 2;
        var sr = (avgE > 0) ? distNm / avgE : 0;
        var line = '#' + (dragIdx + 1) + '\u2192#' + (i + 1)
            + '  Dist: ' + distNm.toFixed(2) + ' NM'
            + '  Brng: ' + brg.toFixed(1) + '\u00B0';
        if (datumType === 'POINT') {
            line += '  DD: ' + distNm.toFixed(2) + ' NM'
                + '  SR: ' + sr.toFixed(2);
        }
        lines.push(line);
    });

    if (lines.length > 0) {
        tooltipEl.textContent = lines.join('\n');
        tooltipEl.style.display = 'block';
        tooltipEl.style.whiteSpace = 'pre';
        tooltipOverlay.setPosition(mapCoordinate);
    }
}

function hideTooltip() {
    var tooltipEl = document.getElementById('datumDragTooltip');
    if (tooltipEl) tooltipEl.style.display = 'none';
}

function haversine(lat1, lon1, lat2, lon2) {
    var R = 6371000;
    var dLat = (lat2 - lat1) * Math.PI / 180;
    var dLon = (lon2 - lon1) * Math.PI / 180;
    var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function bearingCalc(lat1, lon1, lat2, lon2) {
    var dLon = (lon2 - lon1) * Math.PI / 180;
    var y = Math.sin(dLon) * Math.cos(lat2 * Math.PI / 180);
    var x = Math.cos(lat1 * Math.PI / 180) * Math.sin(lat2 * Math.PI / 180) -
            Math.sin(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.cos(dLon);
    return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
}

export function getDatumSource() {
    return datumSource;
}
