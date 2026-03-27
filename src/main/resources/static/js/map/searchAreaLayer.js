/**
 * Search area layer: PPT-style rectangle manipulation.
 *
 * Features:
 *   - Draw rectangle on map
 *   - 4 corner handles: drag to resize (opposite corner fixed)
 *   - 1 rotation handle (top-center, above): drag to rotate around center
 *   - Body drag: move entire rectangle
 *   - Tooltip on hover: SRU name, area, POC, C, POD, POS
 */

let searchAreaSource = null;
let searchAreaLayer = null;
let handleSource = null;
let handleLayer = null;
let drawInteraction = null;
let currentMap = null;
let selectedFeature = null;
let tooltipOverlay = null;

// Internal model per feature: { cx, cy, w, h, rotation(rad), sruId, colorIndex }
let featureModels = new Map();

const SRU_COLORS = [
    '#2196F3', '#4CAF50', '#FF9800', '#9C27B0',
    '#F44336', '#00BCD4', '#795548', '#607D8B'
];

const HANDLE_RADIUS = 6;
const ROTATION_HANDLE_OFFSET = 30; // pixels above top edge

export function initSearchAreaLayer(map) {
    currentMap = map;

    // Main search area layer
    searchAreaSource = new ol.source.Vector();
    searchAreaLayer = new ol.layer.Vector({
        source: searchAreaSource,
        style: function(feature) {
            var colorIdx = feature.get('colorIndex') || 0;
            var color = SRU_COLORS[colorIdx % SRU_COLORS.length];
            var isSelected = (feature === selectedFeature);
            return new ol.style.Style({
                stroke: new ol.style.Stroke({
                    color: color,
                    width: isSelected ? 3 : 2,
                    lineDash: isSelected ? [6, 4] : undefined
                }),
                fill: new ol.style.Fill({ color: color + '33' })
            });
        },
        zIndex: 30
    });
    map.addLayer(searchAreaLayer);

    // Handle layer (corners + rotation)
    handleSource = new ol.source.Vector();
    handleLayer = new ol.layer.Vector({
        source: handleSource,
        style: function(feature) {
            var type = feature.get('handleType');
            if (type === 'rotation') {
                return new ol.style.Style({
                    image: new ol.style.RegularShape({
                        points: 4,
                        radius: 8,
                        angle: Math.PI / 4,
                        fill: new ol.style.Fill({ color: '#FF9800' }),
                        stroke: new ol.style.Stroke({ color: '#FFF', width: 2 })
                    })
                });
            }
            // Corner handle
            return new ol.style.Style({
                image: new ol.style.Circle({
                    radius: HANDLE_RADIUS,
                    fill: new ol.style.Fill({ color: '#FFFFFF' }),
                    stroke: new ol.style.Stroke({ color: '#333', width: 2 })
                })
            });
        },
        zIndex: 40
    });
    map.addLayer(handleLayer);

    // Tooltip overlay
    var tooltipEl = document.getElementById('searchAreaTooltip');
    if (tooltipEl) {
        tooltipOverlay = new ol.Overlay({
            element: tooltipEl,
            positioning: 'bottom-center',
            offset: [0, -12],
            stopEvent: false
        });
        map.addOverlay(tooltipOverlay);
    }

    // ── Pointer events ──
    var dragState = null; // { type: 'move'|'corner'|'rotation', feature, cornerIdx, startCoord, startModel }

    // Get DragPan interaction to disable during feature drag
    var dragPanInteraction = null;
    map.getInteractions().forEach(function(interaction) {
        if (interaction instanceof ol.interaction.DragPan) {
            dragPanInteraction = interaction;
        }
    });

    map.on('pointerdown', function(evt) {
        if (evt.originalEvent.button !== 0) return; // left click only

        // Check handles first
        var handleHit = map.forEachFeatureAtPixel(evt.pixel, function(f) {
            return f.get('handleType') ? f : null;
        }, { layerFilter: function(l) { return l === handleLayer; } });

        if (handleHit && selectedFeature) {
            var model = featureModels.get(selectedFeature.ol_uid);
            if (!model) return;
            if (dragPanInteraction) dragPanInteraction.setActive(false);
            dragState = {
                type: handleHit.get('handleType') === 'rotation' ? 'rotation' : 'corner',
                feature: selectedFeature,
                cornerIdx: handleHit.get('cornerIdx'),
                startCoord: evt.coordinate,
                startModel: Object.assign({}, model)
            };
            return;
        }

        // Check search area body
        var bodyHit = map.forEachFeatureAtPixel(evt.pixel, function(f) {
            return f.get('sruId') ? f : null;
        }, { layerFilter: function(l) { return l === searchAreaLayer; } });

        if (bodyHit) {
            selectFeature(bodyHit);
            var model = featureModels.get(bodyHit.ol_uid);
            if (!model) return;
            if (dragPanInteraction) dragPanInteraction.setActive(false);
            dragState = {
                type: 'move',
                feature: bodyHit,
                startCoord: evt.coordinate,
                startModel: Object.assign({}, model)
            };
            return;
        }

        // Click on empty area → deselect
        selectFeature(null);
    });

    map.on('pointerdrag', function(evt) {
        if (!dragState) return;
        evt.preventDefault();

        var dx = evt.coordinate[0] - dragState.startCoord[0];
        var dy = evt.coordinate[1] - dragState.startCoord[1];
        var sm = dragState.startModel;

        if (dragState.type === 'move') {
            var model = featureModels.get(dragState.feature.ol_uid);
            model.cx = sm.cx + dx;
            model.cy = sm.cy + dy;
            rebuildFeatureGeometry(dragState.feature);
            updateHandles();
        } else if (dragState.type === 'corner') {
            resizeFromCorner(dragState, evt.coordinate);
        } else if (dragState.type === 'rotation') {
            rotateFromHandle(dragState, evt.coordinate);
        }

        // Real-time tooltip during drag
        showTooltipForFeature(dragState.feature, evt.coordinate);
    });

    map.on('pointerup', function() {
        if (dragState) {
            dragState = null;
            if (dragPanInteraction) dragPanInteraction.setActive(true);
            fireSearchAreaChanged();
        }
    });

    // Tooltip on hover
    map.on('pointermove', function(evt) {
        if (evt.dragging) { hideTooltip(); return; }
        var feature = map.forEachFeatureAtPixel(evt.pixel, function(f) {
            return f.get('sruId') ? f : null;
        }, { layerFilter: function(l) { return l === searchAreaLayer; } });

        if (feature) {
            showTooltipForFeature(feature, evt.coordinate);
        } else {
            hideTooltip();
        }
    });

    // Rotation input apply button
    var applyBtn = document.getElementById('applyRotationBtn');
    if (applyBtn) {
        applyBtn.addEventListener('click', function() {
            if (!selectedFeature) { alert('Select a search area first.'); return; }
            var model = featureModels.get(selectedFeature.ol_uid);
            if (!model) return;
            var deg = parseFloat(document.getElementById('rotationInput').value) || 0;
            model.rotation = deg * Math.PI / 180;
            rebuildFeatureGeometry(selectedFeature);
            updateHandles();
            fireSearchAreaChanged();
        });
    }

    return { searchAreaSource, searchAreaLayer };
}

// ─── Selection ───

function selectFeature(feature) {
    selectedFeature = feature;
    searchAreaLayer.changed();
    if (feature) {
        updateHandles();
        var model = featureModels.get(feature.ol_uid);
        if (model) {
            var rotInput = document.getElementById('rotationInput');
            if (rotInput) rotInput.value = Math.round(model.rotation * 180 / Math.PI);
        }
    } else {
        handleSource.clear();
    }
}

// ─── Handle management ───

function updateHandles() {
    handleSource.clear();
    if (!selectedFeature) return;
    var model = featureModels.get(selectedFeature.ol_uid);
    if (!model) return;

    var corners = getCorners(model);
    // 4 corner handles
    for (var i = 0; i < 4; i++) {
        var hf = new ol.Feature({
            geometry: new ol.geom.Point(corners[i]),
            handleType: 'corner',
            cornerIdx: i
        });
        handleSource.addFeature(hf);
    }

    // Rotation handle: above the top edge center
    var topMidX = (corners[0][0] + corners[1][0]) / 2;
    var topMidY = (corners[0][1] + corners[1][1]) / 2;
    // Offset outward (perpendicular to top edge, toward outside)
    var res = currentMap.getView().getResolution();
    var offset = ROTATION_HANDLE_OFFSET * res;
    var perpX = -Math.sin(model.rotation);
    var perpY = Math.cos(model.rotation);
    var rhf = new ol.Feature({
        geometry: new ol.geom.Point([topMidX + perpX * offset, topMidY + perpY * offset]),
        handleType: 'rotation'
    });
    handleSource.addFeature(rhf);
}

// ─── Geometry helpers ───

function getCorners(model) {
    var hw = model.w / 2, hh = model.h / 2;
    var cos = Math.cos(model.rotation), sin = Math.sin(model.rotation);
    // Corners: TL, TR, BR, BL (relative to rotation)
    var local = [[-hw, hh], [hw, hh], [hw, -hh], [-hw, -hh]];
    return local.map(function(p) {
        return [
            model.cx + p[0] * cos - p[1] * sin,
            model.cy + p[0] * sin + p[1] * cos
        ];
    });
}

function rebuildFeatureGeometry(feature) {
    var model = featureModels.get(feature.ol_uid);
    if (!model) return;
    var corners = getCorners(model);
    corners.push(corners[0]); // close ring
    feature.setGeometry(new ol.geom.Polygon([corners]));
    feature.set('rotation', model.rotation * 180 / Math.PI);
}

// ─── Resize (opposite corner fixed) ───

function resizeFromCorner(state, coord) {
    var model = featureModels.get(state.feature.ol_uid);
    if (!model) return;
    var sm = state.startModel;
    var ci = state.cornerIdx;

    // Opposite corner index: 0↔2, 1↔3
    var oi = (ci + 2) % 4;
    var startCorners = getCornersFromModel(sm);
    var fixedCorner = startCorners[oi];

    // New center = midpoint of fixed corner and current mouse position
    var newCx = (fixedCorner[0] + coord[0]) / 2;
    var newCy = (fixedCorner[1] + coord[1]) / 2;

    // Compute new width/height in local (rotated) coordinates
    var dx = coord[0] - fixedCorner[0];
    var dy = coord[1] - fixedCorner[1];
    var cos = Math.cos(-sm.rotation), sin = Math.sin(-sm.rotation);
    var localDx = dx * cos - dy * sin;
    var localDy = dx * sin + dy * cos;

    model.cx = newCx;
    model.cy = newCy;
    model.w = Math.max(100, Math.abs(localDx));
    model.h = Math.max(100, Math.abs(localDy));
    model.rotation = sm.rotation;

    rebuildFeatureGeometry(state.feature);
    updateHandles();
}

function getCornersFromModel(m) {
    var hw = m.w / 2, hh = m.h / 2;
    var cos = Math.cos(m.rotation), sin = Math.sin(m.rotation);
    var local = [[-hw, hh], [hw, hh], [hw, -hh], [-hw, -hh]];
    return local.map(function(p) {
        return [m.cx + p[0] * cos - p[1] * sin, m.cy + p[0] * sin + p[1] * cos];
    });
}

// ─── Rotate ───

function rotateFromHandle(state, coord) {
    var model = featureModels.get(state.feature.ol_uid);
    if (!model) return;
    var angle = Math.atan2(coord[1] - model.cy, coord[0] - model.cx);
    // Rotation handle is at top (90° from width axis), so subtract PI/2
    model.rotation = angle - Math.PI / 2;
    rebuildFeatureGeometry(state.feature);
    updateHandles();
}

// ─── Draw interaction ───

export function startDrawSearchArea(sruId, colorIndex, onDrawEnd) {
    if (drawInteraction) {
        currentMap.removeInteraction(drawInteraction);
    }
    selectFeature(null);

    drawInteraction = new ol.interaction.Draw({
        source: searchAreaSource,
        type: 'Circle',
        geometryFunction: ol.interaction.Draw.createBox()
    });

    drawInteraction.on('drawend', function(event) {
        var feature = event.feature;
        feature.set('sruId', sruId);
        feature.set('colorIndex', colorIndex);
        feature.set('rotation', 0);

        // Extract model from drawn geometry
        var geom = feature.getGeometry();
        var ext = geom.getExtent();
        var cx = (ext[0] + ext[2]) / 2;
        var cy = (ext[1] + ext[3]) / 2;
        var w = ext[2] - ext[0];
        var h = ext[3] - ext[1];

        featureModels.set(feature.ol_uid, {
            cx: cx, cy: cy, w: w, h: h, rotation: 0,
            sruId: sruId, colorIndex: colorIndex
        });

        // Rebuild as clean rectangle
        rebuildFeatureGeometry(feature);

        currentMap.removeInteraction(drawInteraction);
        drawInteraction = null;

        selectFeature(feature);
        if (onDrawEnd) onDrawEnd(feature);
    });

    currentMap.addInteraction(drawInteraction);
}

// ─── Programmatic add (GA/Baseline results) ───

export function addSearchAreaFeature(area, colorIndex) {
    var rot = (area.rotation || 0) * Math.PI / 180;
    var model = {
        cx: area.centerX, cy: area.centerY,
        w: area.width, h: area.height,
        rotation: rot,
        sruId: area.sruId, colorIndex: colorIndex || 0
    };

    var corners = getCorners(model);
    corners.push(corners[0]);

    var feature = new ol.Feature({
        geometry: new ol.geom.Polygon([corners]),
        sruId: area.sruId,
        colorIndex: colorIndex || 0,
        rotation: area.rotation || 0
    });

    featureModels.set(feature.ol_uid, model);
    searchAreaSource.addFeature(feature);
    return feature;
}

// ─── Clear ───

export function clearSearchAreas() {
    if (searchAreaSource) searchAreaSource.clear();
    if (handleSource) handleSource.clear();
    featureModels.clear();
    selectedFeature = null;
}

// ─── Tooltip ───

function showTooltipForFeature(feature, coordinate) {
    var tooltipEl = document.getElementById('searchAreaTooltip');
    if (!tooltipEl || !tooltipOverlay) return;

    var sruId = feature.get('sruId');
    var sruName = sruId;
    var sruData = null;

    if (window._sruListCache) {
        sruData = window._sruListCache.find(function(s) { return s.id === sruId; });
        if (sruData) sruName = sruData.name;
    }

    var model = featureModels.get(feature.ol_uid);
    if (!model) return;

    var center = [model.cx, model.cy];
    var lonlat = ol.proj.transform(center, 'EPSG:3857', 'EPSG:4326');
    var lat = lonlat[1];
    var cosFactor = Math.cos(lat * Math.PI / 180);

    var widthNm = model.w * cosFactor / 1852;
    var heightNm = model.h / 1852;
    var areaNm2 = widthNm * heightNm;

    var poc = '-', C = '-', POD = '-', POS = '-';

    if (window._pocRasterCache && sruData) {
        poc = sumPocInFeature(window._pocRasterCache, feature);
        var wCorr = (sruData.correctedSweepWidth != null) ? sruData.correctedSweepWidth : sruData.calculatedSweepWidth;
        var Z = wCorr * sruData.searchSpeed * sruData.endurance;
        var cVal = (areaNm2 > 0) ? Z / areaNm2 : 0;
        var podVal = 1 - Math.exp(-cVal);
        var posVal = poc * podVal;

        C = cVal.toFixed(2);
        POD = podVal.toFixed(3);
        POS = posVal.toFixed(3);
        poc = poc.toFixed(3);
    }

    tooltipEl.querySelector('.tooltip-name').textContent = sruName;
    tooltipEl.querySelector('.tooltip-area').textContent = 'Area: ' + areaNm2.toFixed(1) + ' NM\u00B2';
    tooltipEl.querySelector('.tooltip-poc').textContent = 'POC: ' + poc;
    tooltipEl.querySelector('.tooltip-c').textContent = 'C: ' + C;
    tooltipEl.querySelector('.tooltip-pod').textContent = 'POD: ' + POD;
    tooltipEl.querySelector('.tooltip-pos').textContent = 'POS: ' + POS;

    tooltipEl.style.display = 'block';
    tooltipOverlay.setPosition(coordinate);
}

function hideTooltip() {
    var tooltipEl = document.getElementById('searchAreaTooltip');
    if (tooltipEl) tooltipEl.style.display = 'none';
}

function sumPocInFeature(rasterData, feature) {
    if (!rasterData || !rasterData.data) return 0;
    var geom = feature.getGeometry();
    var extent = geom.getExtent();
    var originX = rasterData.originX, originY = rasterData.originY;
    var cellSize = rasterData.cellSize;
    var rows = rasterData.rows, cols = rasterData.cols, data = rasterData.data;

    var colMin = Math.max(0, Math.floor((extent[0] - originX) / cellSize));
    var colMax = Math.min(cols - 1, Math.floor((extent[2] - originX) / cellSize));
    var rowMin = Math.max(0, Math.floor((extent[1] - originY) / cellSize));
    var rowMax = Math.min(rows - 1, Math.floor((extent[3] - originY) / cellSize));

    var sum = 0;
    for (var r = rowMin; r <= rowMax; r++) {
        for (var c = colMin; c <= colMax; c++) {
            var cx = originX + (c + 0.5) * cellSize;
            var cy = originY + (r + 0.5) * cellSize;
            if (geom.intersectsCoordinate([cx, cy])) {
                sum += data[r][c];
            }
        }
    }
    return sum;
}

// ─── Events ───

function fireSearchAreaChanged() {
    document.dispatchEvent(new CustomEvent('searchAreaChanged'));
}

// ─── Exports ───

export function getSearchAreaSource() { return searchAreaSource; }
export function getSelectedFeature() { return selectedFeature; }

export function setModifyCallback(callback) {
    // Now handled via 'searchAreaChanged' custom event
    document.addEventListener('searchAreaChanged', callback);
}

/**
 * Get all search area models for API evaluation.
 */
export function getSearchAreaModels() {
    var models = [];
    searchAreaSource.getFeatures().forEach(function(feature) {
        var m = featureModels.get(feature.ol_uid);
        if (m) {
            models.push({
                sruId: m.sruId,
                centerX: m.cx,
                centerY: m.cy,
                width: m.w,
                height: m.h,
                rotation: m.rotation * 180 / Math.PI
            });
        }
    });
    return models;
}
