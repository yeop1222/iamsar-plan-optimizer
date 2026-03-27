/**
 * IAMSAR Baseline mode.
 */
import { addSearchAreaFeature, clearSearchAreas } from '../map/searchAreaLayer.js';

export function initBaselinePlan(map) {
    document.getElementById('runBaselineBtn').addEventListener('click', function() {
        generateBaseline();
    });
}

function generateBaseline() {
    $.ajax({
        url: '/api/search-plan/baseline/generate',
        type: 'POST',
        contentType: 'application/json; charset=utf-8',
        success: function(result) {
            clearSearchAreas();
            if (result.areas) {
                result.areas.forEach(function(area, idx) {
                    addSearchAreaFeature(area, idx);
                });
            }

            var event = new CustomEvent('planResultReady', { detail: result });
            document.dispatchEvent(event);
        },
        error: function(xhr) {
            alert('Baseline generation failed: ' + (xhr.responseJSON ? xhr.responseJSON.message : xhr.statusText));
        }
    });
}