/**
 * Main application entry point.
 */
import { initMap, getMap } from './map/mapInit.js';
import { initDatumLayer } from './map/datumLayer.js';
import { initPocLayer, showPocHeatmap, showContour50, setPocOpacity, removePocHeatmap, clearContour } from './map/pocLayer.js';
import { initSearchAreaLayer, clearSearchAreas, addSearchAreaFeature } from './map/searchAreaLayer.js';
import { initDatumManager } from './datum/datumManager.js';
import { initSruManager, loadSruList, getSruList } from './sru/sruManager.js';
import { initManualPlan } from './plan/manualPlan.js';
import { initGaPlan } from './plan/gaPlan.js';
import { initBaselinePlan } from './plan/baselinePlan.js';

(function() {
    'use strict';

    // Initialize map
    var map = initMap();
    window._map = map;

    // Initialize layers
    initDatumLayer(map);
    initPocLayer(map);
    initSearchAreaLayer(map);

    // Initialize UI managers
    initDatumManager(map);
    initSruManager();
    initManualPlan(map);
    initGaPlan(map);
    initBaselinePlan(map);

    // Load initial SRU list
    loadSruList();

    // Tab switching
    document.querySelectorAll('.tab-button').forEach(function(btn) {
        btn.addEventListener('click', function() {
            document.querySelectorAll('.tab-button').forEach(function(b) { b.classList.remove('active'); });
            document.querySelectorAll('.tab-content').forEach(function(c) { c.classList.remove('active'); });
            btn.classList.add('active');
            var tabId = btn.getAttribute('data-tab');
            document.getElementById(tabId).classList.add('active');
        });
    });

    // Plan mode switching
    document.querySelectorAll('.mode-button').forEach(function(btn) {
        btn.addEventListener('click', function() {
            document.querySelectorAll('.mode-button').forEach(function(b) { b.classList.remove('active'); });
            document.querySelectorAll('.plan-mode-content').forEach(function(c) { c.classList.remove('active'); });
            btn.classList.add('active');
            var modeId = btn.getAttribute('data-mode') + 'Mode';
            document.getElementById(modeId).classList.add('active');
        });
    });

    // Raster generated event -> show heatmap
    document.addEventListener('rasterGenerated', function(e) {
        var result = e.detail;
        if (result.extent) {
            showPocHeatmap(map, result.extent);
        }
        if (result.contour50) {
            showContour50(result.contour50);
        }

        // Cache raster data for frontend calculations
        $.ajax({
            url: '/api/raster/data',
            type: 'GET',
            success: function(rasterData) {
                var cacheEvent = new CustomEvent('rasterDataLoaded', { detail: rasterData });
                document.dispatchEvent(cacheEvent);
            }
        });

        // Fit map to raster extent
        if (result.extent) {
            map.getView().fit(result.extent, { padding: [50, 50, 50, 50] });
        }
    });

    // Datum reset -> clear everything
    document.addEventListener('datumReset', function() {
        removePocHeatmap(map);
        clearContour();
        clearSearchAreas();
        var ctrl = document.getElementById('opacityControl');
        if (ctrl) ctrl.style.display = 'none';
        window._pocRasterCache = null;
        // Clear results
        var resultSection = document.getElementById('resultSection');
        resultSection.style.display = 'none';
        resultSection._lastResult = null;
        // Clear saved results
        savedResults.length = 0;
        renderSavedResults();
    });

    // Plan result display + auto-save (GA/Baseline only; Manual uses Save button)
    document.addEventListener('planResultReady', function(e) {
        var result = e.detail;
        displayPlanResult(result);

        var method = result.method || '';
        var saveBtn = document.getElementById('saveResultBtn');
        saveBtn.style.display = 'none';

        if (method.indexOf('GA') >= 0) {
            autoSaveResult(result);
        } else if (method.indexOf('BASELINE') >= 0) {
            // Baseline: skip if duplicate (same method + same totalPOS)
            var isDup = savedResults.some(function(s) {
                return s.method === result.method
                    && Math.abs(s.totalPOS - result.totalPOS) < 0.0001;
            });
            if (!isDup) {
                autoSaveResult(result);
            }
        }
        // Manual: show Save button
        if (method.indexOf('GA') < 0 && method.indexOf('BASELINE') < 0) {
            saveBtn.style.display = '';
        }
    });

    // Opacity slider
    document.getElementById('opacitySlider').addEventListener('input', function(e) {
        setPocOpacity(parseFloat(e.target.value));
    });

    // ─── Saved Results (in-memory, max 10) ───
    var savedResults = [];
    var MAX_SAVED = 10;

    // Manual save button
    document.getElementById('saveResultBtn').addEventListener('click', function() {
        var resultSection = document.getElementById('resultSection');
        var data = resultSection._lastResult;
        if (!data) return;
        autoSaveResult(data);
        this.style.display = 'none';
    });

    function autoSaveResult(data) {
        if (!data) return;
        var entry = {
            id: Date.now(),
            timestamp: new Date().toLocaleString(),
            method: data.method || 'Unknown',
            totalPOS: data.totalPOS,
            result: data
        };
        savedResults.unshift(entry);
        if (savedResults.length > MAX_SAVED) {
            savedResults.pop();
        }
        renderSavedResults();
    }

    function renderSavedResults() {
        var section = document.getElementById('savedResultSection');
        var list = document.getElementById('savedResultList');
        var badge = document.getElementById('savedCountBadge');

        if (savedResults.length === 0) {
            section.style.display = 'none';
            return;
        }

        section.style.display = 'block';
        badge.textContent = savedResults.length + '/' + MAX_SAVED;
        list.textContent = '';

        savedResults.forEach(function(entry, idx) {
            var card = document.createElement('div');
            card.className = 'item-card';
            card.style.cursor = 'pointer';

            var title = document.createElement('div');
            title.className = 'item-title';
            title.textContent = '#' + (idx + 1) + ' ' + entry.method;

            var info = document.createElement('div');
            info.style.fontSize = '11px';
            info.style.color = '#666';
            info.textContent = entry.timestamp + ' | POS: ' + (entry.totalPOS != null ? entry.totalPOS.toFixed(3) : '-');

            var actions = document.createElement('div');
            actions.className = 'item-actions';

            var btnStyle = 'padding:2px 8px;font-size:11px;';

            var loadBtn = document.createElement('button');
            loadBtn.className = 'btn-primary';
            loadBtn.textContent = 'Load';
            loadBtn.style.cssText = btnStyle;
            loadBtn.addEventListener('click', (function(e) {
                return function(evt) {
                    evt.stopPropagation();
                    loadSavedResult(e);
                };
            })(entry));

            var expBtn = document.createElement('button');
            expBtn.className = 'btn-secondary';
            expBtn.textContent = 'Export';
            expBtn.style.cssText = btnStyle;
            expBtn.addEventListener('click', (function(e) {
                return function(evt) {
                    evt.stopPropagation();
                    exportSingleResult(e);
                };
            })(entry));

            var delBtn = document.createElement('button');
            delBtn.className = 'btn-danger';
            delBtn.textContent = 'Del';
            delBtn.style.cssText = btnStyle;
            delBtn.addEventListener('click', (function(i) {
                return function(evt) {
                    evt.stopPropagation();
                    savedResults.splice(i, 1);
                    renderSavedResults();
                };
            })(idx));

            actions.appendChild(loadBtn);
            actions.appendChild(expBtn);
            actions.appendChild(delBtn);

            card.appendChild(title);
            card.appendChild(info);
            card.appendChild(actions);
            card.addEventListener('click', (function(e) {
                return function() { loadSavedResult(e); };
            })(entry));

            list.appendChild(card);
        });
    }

    function loadSavedResult(entry) {
        var result = entry.result;
        // Restore search areas on map
        clearSearchAreas();
        if (result.areas) {
            result.areas.forEach(function(area, idx) {
                addSearchAreaFeature(area, idx);
            });
        }
        // Restore result table
        displayPlanResult(result);
    }

    function exportSingleResult(entry) {
        var json = JSON.stringify(entry);
        var encoded = btoa(unescape(encodeURIComponent(json)));
        navigator.clipboard.writeText(encoded).then(function() {
            alert('Copied to clipboard (base64).');
        }, function() {
            prompt('Copy this base64 string:', encoded);
        });
    }

    // Import saved results from base64
    document.getElementById('importSavedBtn').addEventListener('click', function() {
        var encoded = prompt('Paste base64 string:');
        if (!encoded || !encoded.trim()) return;
        try {
            var json = decodeURIComponent(escape(atob(encoded.trim())));
            var entry = JSON.parse(json);
            if (!entry.id || !entry.result) { alert('Invalid format.'); return; }
            // Deduplicate by id
            var exists = savedResults.some(function(e) { return e.id === entry.id; });
            if (exists) { alert('This result is already saved.'); return; }
            savedResults.unshift(entry);
            if (savedResults.length > MAX_SAVED) {
                savedResults.pop();
            }
            renderSavedResults();
            alert('Imported: ' + entry.method + ' (POS: ' + (entry.totalPOS != null ? entry.totalPOS.toFixed(3) : '-') + ')');
        } catch (e) {
            alert('Failed to import: invalid base64 or JSON.');
        }
    });

    // Export current result as JSON file
    document.getElementById('exportResultBtn').addEventListener('click', function() {
        var resultSection = document.getElementById('resultSection');
        if (resultSection.style.display === 'none') return;

        var data = resultSection._lastResult;
        if (!data) return;

        var blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'search-plan-result.json';
        a.click();
        URL.revokeObjectURL(url);
    });

    function displayPlanResult(result) {
        var section = document.getElementById('resultSection');
        section.style.display = 'block';
        section._lastResult = result;

        var tbody = document.getElementById('resultTableBody');
        tbody.textContent = '';

        if (result.sruResults) {
            result.sruResults.forEach(function(sr) {
                var tr = document.createElement('tr');
                var tdName = document.createElement('td');
                tdName.textContent = sr.sruName || sr.sruId;
                var tdPoc = document.createElement('td');
                tdPoc.textContent = (sr.poc != null) ? sr.poc.toFixed(3) : '-';
                var tdCf = document.createElement('td');
                tdCf.textContent = (sr.coverageFactor != null) ? sr.coverageFactor.toFixed(2) : '-';
                var tdPod = document.createElement('td');
                tdPod.textContent = (sr.pod != null) ? sr.pod.toFixed(3) : '-';
                var tdPos = document.createElement('td');
                tdPos.textContent = (sr.pos != null) ? sr.pos.toFixed(3) : '-';
                tr.appendChild(tdName);
                tr.appendChild(tdPoc);
                tr.appendChild(tdCf);
                tr.appendChild(tdPod);
                tr.appendChild(tdPos);
                tbody.appendChild(tr);
            });
        }

        var totalEl = document.getElementById('totalPosValue');
        totalEl.textContent = (result.totalPOS != null) ? result.totalPOS.toFixed(3) : '-';
    }

})();