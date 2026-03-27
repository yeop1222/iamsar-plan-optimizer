/**
 * Manual search plan mode.
 */
import { startDrawSearchArea, getSearchAreaSource, setModifyCallback, clearSearchAreas, getSearchAreaModels } from '../map/searchAreaLayer.js';
import { getSruList, getSruById } from '../sru/sruManager.js';

let pocRasterCache = null;

export function initManualPlan(map) {
    // Draw search area button
    document.getElementById('drawSearchAreaBtn').addEventListener('click', function() {
        var sruSelect = document.getElementById('manualSruSelect');
        var sruId = sruSelect.value;
        if (!sruId) {
            alert('Please select an SRU first.');
            return;
        }
        var sruList = getSruList();
        var idx = sruList.findIndex(function(s) { return s.id === sruId; });
        startDrawSearchArea(sruId, idx >= 0 ? idx : 0, function() {
            evaluateSearchAreas();
        });
    });

    // Modify callback
    setModifyCallback(function() {
        evaluateSearchAreas();
    });

    // Rotation apply → re-evaluate
    document.addEventListener('searchAreaChanged', function() {
        evaluateSearchAreas();
    });

    // Listen for raster data cache
    document.addEventListener('rasterDataLoaded', function(e) {
        pocRasterCache = e.detail;
        window._pocRasterCache = e.detail;
    });
}

function evaluateSearchAreas() {
    var areas = getSearchAreaModels();
    if (areas.length === 0) return;

    $.ajax({
        url: '/api/search-plan/manual/evaluate',
        type: 'POST',
        contentType: 'application/json; charset=utf-8',
        data: JSON.stringify({ areas: areas }),
        success: function(result) {
            displayResult(result);
        },
        error: function(xhr) {
            console.error('Evaluation failed:', xhr.statusText);
        }
    });
}

function displayResult(result) {
    var event = new CustomEvent('planResultReady', { detail: result });
    document.dispatchEvent(event);
}

export function getPocRasterCache() {
    return pocRasterCache;
}
