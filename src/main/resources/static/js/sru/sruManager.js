/**
 * SRU management UI logic.
 * - Environment + Search Object as global common conditions
 * - SRU type 6 kinds, dynamic size dropdown
 * - Detailed SRU list with W_0, f_v, f_w, f_f
 */

let sruList = [];
var typeCounter = {};  // auto-name counter per type prefix

var TYPE_DEFAULTS = {
    LARGE:  { speed: 8,   time: 3 },
    MEDIUM: { speed: 8,   time: 3 },
    SMALL:  { speed: 8,   time: 3 },
    BOAT:   { speed: 8,   time: 3 },
    ROTARY: { speed: 80,  time: 3 },
    FIXED:  { speed: 150, time: 3 }
};

var TYPE_PREFIX = {
    LARGE: 'Ship', MEDIUM: 'Ship', SMALL: 'Ship', BOAT: 'Boat',
    ROTARY: 'Heli', FIXED: 'Fixed'
};

export function initSruManager() {
    // Toggle altitude row + set defaults based on SRU type
    document.getElementById('sruType').addEventListener('change', function() {
        var type = this.value;
        var isAircraft = (type === 'ROTARY' || type === 'FIXED');
        document.getElementById('altitudeRow').style.display = isAircraft ? '' : 'none';

        // Set default speed/time for this type
        var def = TYPE_DEFAULTS[type];
        if (def) {
            document.getElementById('sruSpeed').value = def.speed;
            document.getElementById('sruEndurance').value = def.time;
        }

        // Update auto-name
        updateAutoName(type);

        // Update available sizes for new type
        updateSizeDropdown();
    });

    // Category change → update size dropdown (needs current SRU type for available sizes)
    document.getElementById('soCategory').addEventListener('change', function() {
        updateSizeDropdown();
    });

    // Apply conditions button
    document.getElementById('applyConditionsBtn').addEventListener('click', function() {
        applyConditions();
    });

    // Register SRU
    document.getElementById('addSruBtn').addEventListener('click', function() {
        registerSru();
    });

    // Initial size dropdown + auto-name
    updateSizeDropdown();
    updateAutoName(document.getElementById('sruType').value);
}

function updateSizeDropdown() {
    var category = document.getElementById('soCategory').value;
    // Use a representative SRU type for size lookup — try current selected
    var sruType = document.getElementById('sruType').value;
    $.ajax({
        url: '/api/sru/available-sizes?type=' + sruType + '&category=' + category,
        type: 'GET',
        success: function(sizes) {
            var sel = document.getElementById('soSize');
            var current = sel.value;
            sel.textContent = '';
            sizes.forEach(function(s) {
                var opt = document.createElement('option');
                opt.value = s;
                opt.textContent = s;
                sel.appendChild(opt);
            });
            // Try to keep previous selection
            if (sizes.indexOf(parseInt(current)) >= 0) {
                sel.value = current;
            }
        }
    });
}

function applyConditions() {
    var payload = {
        environment: {
            visibility: parseFloat(document.getElementById('envVisibility').value),
            windSpeed: parseFloat(document.getElementById('envWindSpeed').value),
            waveHeight: parseFloat(document.getElementById('envWaveHeight').value)
        },
        searchCondition: {
            category: document.getElementById('soCategory').value,
            objectSize: parseInt(document.getElementById('soSize').value)
        }
    };

    $.ajax({
        url: '/api/conditions',
        type: 'POST',
        contentType: 'application/json; charset=utf-8',
        data: JSON.stringify(payload),
        success: function(result) {
            if (result.sruList) {
                sruList = result.sruList;
                window._sruListCache = sruList;
                renderSruList();
                updateSruSelects();
            }
        },
        error: function(xhr) {
            alert('Failed to apply conditions: ' + (xhr.responseJSON ? xhr.responseJSON.message : xhr.statusText));
        }
    });
}

function registerSru() {
    var type = document.getElementById('sruType').value;
    var nameInput = document.getElementById('sruName');
    var name = nameInput.value.trim();

    // If name matches the auto-pattern or is empty, generate a new auto-name
    var prefix = TYPE_PREFIX[type] || type;
    var autoPattern = new RegExp('^' + prefix + '-\\d+$');
    if (!name || autoPattern.test(name)) {
        name = generateAutoName(type);
        nameInput.value = name;
    }

    var speed = parseFloat(document.getElementById('sruSpeed').value);
    var endurance = parseFloat(document.getElementById('sruEndurance').value);
    var isAircraft = (type === 'ROTARY' || type === 'FIXED');
    var altitude = isAircraft ? parseInt(document.getElementById('sruAltitude').value) : 0;
    var fatigue = document.getElementById('sruFatigue').checked;
    var wcorrInput = document.getElementById('sruWcorr').value.trim();
    var correctedSweepWidth = wcorrInput ? parseFloat(wcorrInput) : null;
    if (isNaN(speed) || speed <= 0 || isNaN(endurance) || endurance <= 0) {
        alert('Please enter valid speed and endurance.');
        return;
    }

    var payload = {
        name: name,
        type: type,
        searchSpeed: speed,
        endurance: endurance,
        altitude: altitude,
        fatigue: fatigue,
        correctedSweepWidth: correctedSweepWidth
    };

    $.ajax({
        url: '/api/sru',
        type: 'POST',
        contentType: 'application/json; charset=utf-8',
        data: JSON.stringify(payload),
        success: function() {
            loadSruList();
            clearSruForm();
        },
        error: function(xhr) {
            alert('Failed to register SRU: ' + (xhr.responseJSON ? xhr.responseJSON.message : xhr.statusText));
        }
    });
}

export function loadSruList() {
    $.ajax({
        url: '/api/sru',
        type: 'GET',
        success: function(list) {
            sruList = list;
            window._sruListCache = list;
            renderSruList();
            updateSruSelects();
        }
    });
}

function renderSruList() {
    var listEl = document.getElementById('sruList');
    listEl.textContent = '';

    sruList.forEach(function(sru) {
        var card = document.createElement('div');
        card.className = 'item-card';

        var title = document.createElement('div');
        title.className = 'item-title';
        title.textContent = sru.name + ' (' + sru.type + ')';

        var detail = document.createElement('div');
        detail.className = 'item-detail';
        var lines = [];
        lines.push('V: ' + sru.searchSpeed + 'kts  T: ' + sru.endurance + 'hrs');
        if (sru.type === 'ROTARY' || sru.type === 'FIXED') {
            lines.push('Alt: ' + sru.altitude + 'm');
        }
        lines.push('W\u2080: ' + fmt(sru.uncorrectedSweepWidth)
            + '  f_v: ' + fmt(sru.velocityFactor)
            + '  f_w: ' + fmt(sru.weatherFactor)
            + '  f_f: ' + fmt(sru.fatigueFactor));
        var wCorr = sru.correctedSweepWidth != null ? sru.correctedSweepWidth : sru.calculatedSweepWidth;
        lines.push('W_corr: ' + fmt(wCorr) + ' NM   Z: ' + fmt(sru.searchEffort) + ' NM\u00B2');
        detail.innerHTML = lines.join('<br>');

        var actions = document.createElement('div');
        actions.className = 'item-actions';
        var delBtn = document.createElement('button');
        delBtn.className = 'btn-danger';
        delBtn.textContent = 'Delete';
        delBtn.addEventListener('click', function() { deleteSru(sru.id); });
        actions.appendChild(delBtn);

        card.appendChild(title);
        card.appendChild(detail);
        card.appendChild(actions);
        listEl.appendChild(card);
    });
}

function fmt(v) {
    return (v != null && !isNaN(v)) ? v.toFixed(2) : '-';
}

function deleteSru(id) {
    $.ajax({
        url: '/api/sru/' + id,
        type: 'DELETE',
        success: function() { loadSruList(); }
    });
}

function updateSruSelects() {
    var selects = document.querySelectorAll('#manualSruSelect');
    selects.forEach(function(select) {
        var current = select.value;
        select.textContent = '';
        sruList.forEach(function(sru) {
            var opt = document.createElement('option');
            opt.value = sru.id;
            opt.textContent = sru.name;
            select.appendChild(opt);
        });
        if (current) select.value = current;
    });
}

function clearSruForm() {
    document.getElementById('sruWcorr').value = '';
    // Pre-fill next auto name
    var type = document.getElementById('sruType').value;
    updateAutoName(type);
}

function generateAutoName(type) {
    var prefix = TYPE_PREFIX[type] || type;
    if (!typeCounter[prefix]) typeCounter[prefix] = 0;
    typeCounter[prefix]++;
    return prefix + '-' + typeCounter[prefix];
}

function updateAutoName(type) {
    var prefix = TYPE_PREFIX[type] || type;
    var next = (typeCounter[prefix] || 0) + 1;
    document.getElementById('sruName').value = prefix + '-' + next;
}

export function getSruList() { return sruList; }
export function getSruById(id) {
    return sruList.find(function(s) { return s.id === id; });
}
