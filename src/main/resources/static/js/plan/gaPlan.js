/**
 * GA optimization mode.
 */
import { addSearchAreaFeature, clearSearchAreas } from '../map/searchAreaLayer.js';

export function initGaPlan(map) {
    document.getElementById('runGaBtn').addEventListener('click', function() {
        startGaOptimization();
    });
}

function startGaOptimization() {
    var config = {
        populationSize: parseInt(document.getElementById('gaPopulation').value),
        maxGenerations: parseInt(document.getElementById('gaGenerations').value),
        crossoverRate: parseFloat(document.getElementById('gaCrossover').value),
        mutationRate: parseFloat(document.getElementById('gaMutation').value),
        tournamentSize: 5,
        elitismCount: 2,
        convergenceGenerations: 50
    };

    var progressContainer = document.getElementById('gaProgressContainer');
    var progressBar = document.getElementById('gaProgressBar');
    var progressText = document.getElementById('gaProgressText');
    progressContainer.style.display = 'block';
    progressBar.style.width = '0%';
    progressText.textContent = '0%';

    $.ajax({
        url: '/api/search-plan/ga/optimize',
        type: 'POST',
        contentType: 'application/json; charset=utf-8',
        data: JSON.stringify({ config: config }),
        success: function(result) {
            if (result.optimizationId) {
                connectSSE(result.optimizationId, config.maxGenerations);
            } else {
                // Synchronous result
                handleGaComplete(result);
            }
        },
        error: function(xhr) {
            progressContainer.style.display = 'none';
            alert('GA optimization failed: ' + (xhr.responseJSON ? xhr.responseJSON.message : xhr.statusText));
        }
    });
}

function connectSSE(optimizationId, maxGenerations) {
    var progressBar = document.getElementById('gaProgressBar');
    var progressText = document.getElementById('gaProgressText');

    var eventSource = new EventSource('/api/search-plan/ga/optimize/stream?id=' + optimizationId);

    eventSource.addEventListener('progress', function(e) {
        var data = JSON.parse(e.data);
        var pct = Math.round((data.generation / maxGenerations) * 100);
        progressBar.style.width = pct + '%';
        progressText.textContent = pct + '% (Gen ' + data.generation + ', Best: ' + data.bestFitness.toFixed(4) + ')';
    });

    eventSource.addEventListener('complete', function(e) {
        var result = JSON.parse(e.data);
        eventSource.close();
        handleGaComplete(result);
    });

    eventSource.addEventListener('error', function() {
        eventSource.close();
        document.getElementById('gaProgressContainer').style.display = 'none';
        alert('SSE connection lost.');
    });
}

function handleGaComplete(result) {
    var progressBar = document.getElementById('gaProgressBar');
    var progressText = document.getElementById('gaProgressText');
    progressBar.style.width = '100%';
    progressText.textContent = '100% Complete';

    clearSearchAreas();
    if (result.areas) {
        result.areas.forEach(function(area, idx) {
            addSearchAreaFeature(area, idx);
        });
    }

    var event = new CustomEvent('planResultReady', { detail: result });
    document.dispatchEvent(event);
}