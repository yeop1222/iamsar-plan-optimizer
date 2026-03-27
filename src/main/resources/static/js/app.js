/**
 * Main application entry point.
 */
import { initMap, getMap } from './map/mapInit.js';
import { initDatumLayer } from './map/datumLayer.js';
import { initPocLayer, showPocHeatmap, showContour50, setPocOpacity, removePocHeatmap, clearContour } from './map/pocLayer.js';
import { initSearchAreaLayer } from './map/searchAreaLayer.js';
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

    // Datum reset -> clear raster + contour + opacity control
    document.addEventListener('datumReset', function() {
        removePocHeatmap(map);
        clearContour();
        var ctrl = document.getElementById('opacityControl');
        if (ctrl) ctrl.style.display = 'none';
        window._pocRasterCache = null;
    });

    // Plan result display
    document.addEventListener('planResultReady', function(e) {
        var result = e.detail;
        displayPlanResult(result);
    });

    // Opacity slider
    document.getElementById('opacitySlider').addEventListener('input', function(e) {
        setPocOpacity(parseFloat(e.target.value));
    });

    // Export result
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