const guides = [
    'tool_astronomy',
    'tool_augmented_reality',
    'tool_battery',
    'tool_beacons',
    'tool_bubble_level',
    'tool_cliff_height',
    'tool_climate',
    'tool_clinometer',
    'tool_clock',
    'tool_clouds',
    'tool_convert',
    'tool_diagnostics',
    'tool_flashlight',
    'tool_light_meter',
    'tool_lightning_strike_distance',
    'tool_metal_detector',
    'tool_navigation',
    'tool_notes',
    'tool_packing_lists',
    'tool_paths',
    'tool_pedometer',
    'tool_photo_maps',
    'tool_qr_code_scanner',
    'tool_recommended_apps',
    'tool_ruler',
    'tool_sensors',
    'tool_solar_panel_aligner',
    'tool_tools',
    'tool_temperature_estimation',
    'tool_tides',
    'tool_triangulate_location',
    'tool_water_boil_timer',
    'tool_weather',
    'tool_whistle',
    'tool_white_noise',
    'settings_barometer',
];

// Populate the guide list
const guideList = document.querySelector('#guide-list');
for (const guide of guides) {
    const guideItem = document.createElement('a');
    guideItem.innerText = guide.split('_').slice(1).map(w => w[0].toUpperCase() + w.slice(1)).join(' ');
    guideItem.classList.add('guide-list-item');
    guideItem.href = `guide?id=${guide}`;
    guideList.appendChild(guideItem);
}