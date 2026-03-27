/**
 * Datum CRUD UI logic.
 * - POINT: max 2 points
 * - Shows distance/bearing between points
 * - Map click: single-click for POINT, auto-disable after 1 click
 * - Bearing/distance input from existing point
 * - Editable coordinates in left panel
 * - Locked after raster generation (unlock on reset)
 */
import { renderDatumPoints, clearDatumLayer, enableDatumDrag, disableDatumDrag } from '../map/datumLayer.js';

let datumPoints = [];
let datumType = 'POINT';
let mapClickHandler = null;
let currentMap = null;
let locked = false;  // true after raster generation

export function initDatumManager(map) {
    currentMap = map;

    // Datum type radio
    document.querySelectorAll('input[name="datumType"]').forEach(function(radio) {
        radio.addEventListener('change', function() {
            if (locked) { this.checked = false; return; }
            datumType = this.value;
            renderPointList();
        });
    });

    // Input method radio
    document.querySelectorAll('input[name="inputMethod"]').forEach(function(radio) {
        radio.addEventListener('change', function() {
            if (locked) { this.checked = false; return; }
            document.getElementById('manualInputSection').style.display = 'none';
            document.getElementById('mapClickSection').style.display = 'none';
            document.getElementById('bearingInputSection').style.display = 'none';
            disableMapClick(map);

            if (this.value === 'manual') {
                document.getElementById('manualInputSection').style.display = 'block';
            } else if (this.value === 'map') {
                document.getElementById('mapClickSection').style.display = 'block';
                enableMapClick(map);
            } else if (this.value === 'bearing') {
                document.getElementById('bearingInputSection').style.display = 'block';
                updateBearingFromSelect();
            }
        });
    });

    // Add point button (manual)
    document.getElementById('addDatumPointBtn').addEventListener('click', function() {
        if (locked) { alert('Reset datum to modify. Datum is locked after raster generation.'); return; }
        var lat = parseFloat(document.getElementById('datumLat').value);
        var lon = parseFloat(document.getElementById('datumLon').value);
        var errorNm = parseFloat(document.getElementById('datumError').value);

        if (isNaN(lat) || isNaN(lon) || isNaN(errorNm)) {
            alert('Please enter valid coordinates and error.');
            return;
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            alert('Coordinates out of range.');
            return;
        }
        if (errorNm <= 0) {
            alert('Error must be positive.');
            return;
        }
        if (!canAddPoint()) return;

        addDatumPoint({ latitude: lat, longitude: lon, errorNm: errorNm });
    });

    // Add point button (bearing/distance)
    document.getElementById('addBearingPointBtn').addEventListener('click', function() {
        if (locked) { alert('Reset datum to modify.'); return; }
        if (datumPoints.length === 0) {
            alert('Need at least 1 existing point.');
            return;
        }
        if (!canAddPoint()) return;

        var fromIdx = parseInt(document.getElementById('bearingFromPoint').value);
        var bearingVal = parseFloat(document.getElementById('bearingDeg').value);
        var distNm = parseFloat(document.getElementById('bearingDistNm').value);
        var errorNm = parseFloat(document.getElementById('bearingError').value);

        if (isNaN(bearingVal) || isNaN(distNm) || isNaN(errorNm)) {
            alert('Enter valid bearing, distance, and error.');
            return;
        }

        var from = datumPoints[fromIdx];
        if (!from) { alert('Invalid source point.'); return; }

        var dest = destinationPoint(from.latitude, from.longitude, bearingVal, distNm * 1852);
        addDatumPoint({ latitude: dest[0], longitude: dest[1], errorNm: errorNm });
    });

    // Reset button
    document.getElementById('resetDatumBtn').addEventListener('click', function() {
        resetDatum();
    });

    // Generate raster button
    document.getElementById('generateRasterBtn').addEventListener('click', function() {
        generateRaster();
    });

    // Listen for datum drag updates from datumLayer
    document.addEventListener('datumPointMoved', function(e) {
        if (locked) return;
        var d = e.detail;
        datumPoints[d.index] = { latitude: d.latitude, longitude: d.longitude, errorNm: datumPoints[d.index].errorNm };
        renderPointList();
    });

    document.addEventListener('datumErrorResized', function(e) {
        if (locked) return;
        var d = e.detail;
        datumPoints[d.index].errorNm = d.errorNm;
        renderPointList();
    });
}

function canAddPoint() {
    if (datumType === 'POINT' && datumPoints.length >= 2) {
        alert('POINT datum allows max 2 points.');
        return false;
    }
    return true;
}

function addDatumPoint(point) {
    datumPoints.push(point);
    renderPointList();
    rerender();
    updateBearingFromSelect();
}

function removeDatumPoint(index) {
    if (locked) { alert('Reset datum to modify.'); return; }
    datumPoints.splice(index, 1);
    renderPointList();
    rerender();
    updateBearingFromSelect();
}

function updateDatumPoint(index, field, value) {
    if (locked) return;
    if (!datumPoints[index]) return;
    datumPoints[index][field] = value;
    renderPointList();
    rerender();
}

function rerender() {
    renderDatumPoints({ type: datumType, points: datumPoints }, currentMap);
    if (!locked) {
        enableDatumDrag(currentMap, datumPoints, datumType);
    }
}

/**
 * Render point list with editable fields + distance/bearing info.
 */
function renderPointList() {
    var listEl = document.getElementById('datumPointList');
    listEl.textContent = '';

    // Count badge
    var badge = document.getElementById('datumCountBadge');
    if (badge) {
        var maxLabel = (datumType === 'POINT') ? '/2' : '';
        badge.textContent = datumPoints.length + maxLabel;
    }

    datumPoints.forEach(function(pt, idx) {
        var card = document.createElement('div');
        card.className = 'item-card';
        if (locked) card.style.opacity = '0.7';

        var title = document.createElement('div');
        title.className = 'item-title';
        title.textContent = '#' + (idx + 1);

        // Editable lat
        var editRow1 = document.createElement('div');
        editRow1.className = 'datum-edit-row';
        var lblLat = document.createElement('label');
        lblLat.textContent = 'Lat:';
        var inpLat = document.createElement('input');
        inpLat.type = 'number';
        inpLat.step = '0.0001';
        inpLat.value = pt.latitude.toFixed(4);
        inpLat.disabled = locked;
        inpLat.addEventListener('change', (function(i) {
            return function() { updateDatumPoint(i, 'latitude', parseFloat(this.value)); };
        })(idx));

        var lblLon = document.createElement('label');
        lblLon.textContent = 'Lon:';
        var inpLon = document.createElement('input');
        inpLon.type = 'number';
        inpLon.step = '0.0001';
        inpLon.value = pt.longitude.toFixed(4);
        inpLon.disabled = locked;
        inpLon.addEventListener('change', (function(i) {
            return function() { updateDatumPoint(i, 'longitude', parseFloat(this.value)); };
        })(idx));

        editRow1.appendChild(lblLat);
        editRow1.appendChild(inpLat);
        editRow1.appendChild(lblLon);
        editRow1.appendChild(inpLon);

        // Editable error
        var editRow2 = document.createElement('div');
        editRow2.className = 'datum-edit-row';
        var lblErr = document.createElement('label');
        lblErr.textContent = 'E:';
        var inpErr = document.createElement('input');
        inpErr.type = 'number';
        inpErr.step = '0.1';
        inpErr.value = pt.errorNm;
        inpErr.style.width = '60px';
        inpErr.disabled = locked;
        inpErr.addEventListener('change', (function(i) {
            return function() { updateDatumPoint(i, 'errorNm', parseFloat(this.value)); };
        })(idx));
        var lblNm = document.createElement('span');
        lblNm.textContent = ' NM';
        lblNm.style.fontSize = '11px';

        editRow2.appendChild(lblErr);
        editRow2.appendChild(inpErr);
        editRow2.appendChild(lblNm);

        // Delete
        var actions = document.createElement('div');
        actions.className = 'item-actions';
        if (!locked) {
            var delBtn = document.createElement('button');
            delBtn.className = 'btn-danger';
            delBtn.textContent = 'Delete';
            delBtn.addEventListener('click', (function(i) {
                return function() { removeDatumPoint(i); };
            })(idx));
            actions.appendChild(delBtn);
        }

        card.appendChild(title);
        card.appendChild(editRow1);
        card.appendChild(editRow2);
        card.appendChild(actions);
        listEl.appendChild(card);
    });

    // Distance/bearing/DD/SR: consecutive pairs only (i -> i+1), AREA adds last->first
    var distInfo = document.getElementById('datumDistInfo');
    if (datumPoints.length >= 2) {
        distInfo.style.display = 'block';
        distInfo.textContent = '';

        var pairs = [];
        for (var i = 0; i < datumPoints.length - 1; i++) {
            pairs.push([i, i + 1]);
        }
        if (datumType === 'AREA' && datumPoints.length >= 3) {
            pairs.push([datumPoints.length - 1, 0]);
        }

        pairs.forEach(function(pair) {
            var pi = datumPoints[pair[0]];
            var pj = datumPoints[pair[1]];
            var distM = haversineDist(pi.latitude, pi.longitude, pj.latitude, pj.longitude);
            var distNm = distM / 1852;
            var brg = bearingCalc(pi.latitude, pi.longitude, pj.latitude, pj.longitude);
            var avgE = (pi.errorNm + pj.errorNm) / 2;
            var sr = (avgE > 0) ? distNm / avgE : 0;

            var row = document.createElement('div');
            row.style.marginBottom = '2px';
            var html = '<strong>#' + (pair[0] + 1) + '\u2192#' + (pair[1] + 1) + '</strong> '
                + 'Dist: <strong>' + distNm.toFixed(2) + '</strong> NM, '
                + 'Brng: <strong>' + brg.toFixed(1) + '</strong>\u00B0';
            if (datumType === 'POINT') {
                html += ', DD: <strong>' + distNm.toFixed(2) + '</strong> NM'
                    + ', SR: <strong>' + sr.toFixed(2) + '</strong>';
            }
            row.innerHTML = html;
            distInfo.appendChild(row);
        });
    } else {
        distInfo.style.display = 'none';
    }
}

// ─── Map Click ───
function enableMapClick(map) {
    if (locked) return;
    disableMapClick(map);
    mapClickHandler = function(evt) {
        if (locked || !canAddPoint()) {
            disableMapClick(map);
            return;
        }
        var coord = ol.proj.transform(evt.coordinate, 'EPSG:3857', 'EPSG:4326');
        var errorNm = parseFloat(document.getElementById('datumErrorMapClick').value) || 3.5;
        addDatumPoint({
            latitude: parseFloat(coord[1].toFixed(6)),
            longitude: parseFloat(coord[0].toFixed(6)),
            errorNm: errorNm
        });

        // POINT type: auto-disable after 1 click
        if (datumType === 'POINT') {
            disableMapClick(map);
            var manualRadio = document.querySelector('input[name="inputMethod"][value="manual"]');
            if (manualRadio) {
                manualRadio.checked = true;
                document.getElementById('mapClickSection').style.display = 'none';
                document.getElementById('manualInputSection').style.display = 'block';
            }
        }
    };
    map.on('click', mapClickHandler);
}

function disableMapClick(map) {
    if (mapClickHandler) {
        map.un('click', mapClickHandler);
        mapClickHandler = null;
    }
}

// ─── Bearing from-select ───
function updateBearingFromSelect() {
    var sel = document.getElementById('bearingFromPoint');
    if (!sel) return;
    sel.textContent = '';
    datumPoints.forEach(function(pt, idx) {
        var opt = document.createElement('option');
        opt.value = idx;
        opt.textContent = 'Point #' + (idx + 1);
        sel.appendChild(opt);
    });
}

// ─── Reset (also clears raster) ───
function resetDatum() {
    locked = false;
    datumPoints = [];
    renderPointList();
    clearDatumLayer();
    disableMapClick(currentMap);
    $.ajax({ url: '/api/datum', type: 'DELETE' });
    // Dispatch event so app.js can clear raster
    document.dispatchEvent(new CustomEvent('datumReset'));
}

// ─── Raster generation (locks datum) ───
function generateRaster() {
    if (datumPoints.length === 0) {
        alert('Please add at least one datum point.');
        return;
    }
    var payload = { type: datumType, points: datumPoints };
    $.ajax({
        url: '/api/datum',
        type: 'POST',
        contentType: 'application/json; charset=utf-8',
        data: JSON.stringify(payload),
        success: function() {
            $.ajax({
                url: '/api/raster/generate',
                type: 'POST',
                contentType: 'application/json; charset=utf-8',
                success: function(result) {
                    // Lock datum editing
                    locked = true;
                    disableDatumDrag(currentMap);
                    disableMapClick(currentMap);
                    renderPointList();  // re-render with disabled state
                    document.dispatchEvent(new CustomEvent('rasterGenerated', { detail: result }));
                },
                error: function(xhr) {
                    alert('Raster generation failed: ' + (xhr.responseJSON ? xhr.responseJSON.message : xhr.statusText));
                }
            });
        },
        error: function(xhr) {
            alert('Failed to save datum: ' + (xhr.responseJSON ? xhr.responseJSON.message : xhr.statusText));
        }
    });
}

// ─── Geo Utils ───
function haversineDist(lat1, lon1, lat2, lon2) {
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

function destinationPoint(lat, lon, bearingDeg, distM) {
    var R = 6371000;
    var brg = bearingDeg * Math.PI / 180;
    var lat1 = lat * Math.PI / 180;
    var lon1 = lon * Math.PI / 180;
    var d = distM / R;
    var lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(brg));
    var lon2 = lon1 + Math.atan2(Math.sin(brg) * Math.sin(d) * Math.cos(lat1),
                                  Math.cos(d) - Math.sin(lat1) * Math.sin(lat2));
    return [lat2 * 180 / Math.PI, lon2 * 180 / Math.PI];
}

export function getDatumPoints() { return datumPoints; }
export function getDatumType() { return datumType; }
export function isDatumLocked() { return locked; }
