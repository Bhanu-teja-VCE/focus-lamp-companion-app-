// ============================================================================
// FOCUS LAMP — INTERACTIVE LANDING PAGE SCRIPT
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    // --- Elements ---
    const stateButtons = document.querySelectorAll('.state-btn');
    const usageRange = document.getElementById('usageRange');
    const usageValueText = document.getElementById('usageValueText');

    // Hero Lamp Elements
    const heroLampGlow = document.getElementById('heroLampGlow');
    const heroLampDome = document.getElementById('heroLampDome');
    const heroStatusPill = document.getElementById('heroStatusPill');
    const heroStatusText = document.getElementById('heroStatusText');

    // Simulator Lamp Elements
    const simAura = document.getElementById('simAura');
    const simDome = document.getElementById('simDome');
    
    // Telemetry Elements
    const telemetryEndpoint = document.getElementById('telemetryEndpoint');
    const telemetryColor = document.getElementById('telemetryColor');
    const telemetryAccess = document.getElementById('telemetryAccess');
    const telemetryPsych = document.getElementById('telemetryPsych');

    // Background Orbs
    const bgOrb1 = document.getElementById('bgOrb1');
    const bgOrb2 = document.getElementById('bgOrb2');

    // Default Configuration
    const LIMIT_MINUTES = 45;

    // State Mapping Configuration
    const stateConfig = {
        focus: {
            name: "Focus Mode Active",
            endpoint: "GET /focus",
            colorName: "Green (Pin 14 PWM 255)",
            glowColor: "#22c55e",
            glowShadow: "0 0 50px rgba(34, 197, 94, 0.7)",
            domeBg: "radial-gradient(circle at 50% 30%, #ffffff, #22c55e)",
            accessText: "Allowed",
            accessClass: "green",
            psychText: "Unaware focus state; ambient green reinforces calm work rhythm.",
            sliderVal: 15
        },
        warning: {
            name: "Warning Nudge (Approaching Limit)",
            endpoint: "GET /warning",
            colorName: "White (Pin 27 PWM 255)",
            glowColor: "#e2e8f0",
            glowShadow: "0 0 50px rgba(226, 232, 240, 0.7)",
            domeBg: "radial-gradient(circle at 50% 30%, #ffffff, #cbd5e1)",
            accessText: "Warning Alert",
            accessClass: "green",
            psychText: "Subconscious visual nudge; user becomes mindful of screen time without sudden cutoff.",
            sliderVal: 38
        },
        distraction: {
            name: "Distraction Limit Breached",
            endpoint: "GET /distraction",
            colorName: "Red (Pin 13 PWM 255)",
            glowColor: "#ef4444",
            glowShadow: "0 0 60px rgba(239, 68, 68, 0.8)",
            domeBg: "radial-gradient(circle at 50% 30%, #ffffff, #ef4444)",
            accessText: "Blocked / Restricted",
            accessClass: "red",
            psychText: "Active intervention; physical red light combined with mobile network block stops app looping.",
            sliderVal: 50
        }
    };

    // --- State Update Handler ---
    function updateState(stateKey, updateSlider = true) {
        const config = stateConfig[stateKey];
        if (!config) return;

        // 1. Active Button Toggle
        stateButtons.forEach(btn => {
            if (btn.dataset.state === stateKey) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });

        // 2. Update Slider Value if needed
        if (updateSlider && usageRange) {
            usageRange.value = config.sliderVal;
            usageValueText.textContent = `${config.sliderVal} / ${LIMIT_MINUTES} mins`;
        }

        // 3. Update Hero Lamp Visuals
        if (heroLampGlow && heroLampDome) {
            heroLampGlow.style.background = config.glowColor;
            heroLampGlow.style.boxShadow = config.glowShadow;
            heroLampDome.style.background = config.domeBg;
            heroLampDome.style.boxShadow = config.glowShadow;
        }

        if (heroStatusText && heroStatusPill) {
            heroStatusText.textContent = config.name;
            const dot = heroStatusPill.querySelector('.status-dot');
            if (dot) {
                dot.style.background = config.glowColor;
                dot.style.boxShadow = `0 0 10px ${config.glowColor}`;
            }
        }

        // 4. Update Simulator Lamp Visuals
        if (simAura && simDome) {
            simAura.style.background = config.glowColor;
            simDome.style.background = config.domeBg;
            simDome.style.boxShadow = config.glowShadow;
        }

        // 5. Update Telemetry Data
        if (telemetryEndpoint) telemetryEndpoint.textContent = config.endpoint;
        if (telemetryColor) telemetryColor.textContent = config.colorName;
        if (telemetryAccess) {
            telemetryAccess.textContent = config.accessText;
            telemetryAccess.className = `t-badge ${config.accessClass}`;
        }
        if (telemetryPsych) telemetryPsych.textContent = config.psychText;

        // 6. Adjust Ambient Orbs
        if (bgOrb1) bgOrb1.style.background = config.glowColor;
    }

    // --- Event Listeners for State Buttons ---
    stateButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const state = btn.dataset.state;
            updateState(state, true);
        });
    });

    // --- Event Listener for Usage Slider ---
    if (usageRange) {
        usageRange.addEventListener('input', (e) => {
            const minutes = parseInt(e.target.value, 10);
            usageValueText.textContent = `${minutes} / ${LIMIT_MINUTES} mins`;

            // Calculate percentage based on 45m limit
            const ratio = minutes / LIMIT_MINUTES;

            if (ratio < 0.75) {
                updateState('focus', false);
            } else if (ratio >= 0.75 && ratio < 1.0) {
                updateState('warning', false);
            } else {
                updateState('distraction', false);
            }
        });
    }

    // --- Ambient Parallax Cursor Movement ---
    document.addEventListener('mousemove', (e) => {
        const x = (e.clientX / window.innerWidth - 0.5) * 40;
        const y = (e.clientY / window.innerHeight - 0.5) * 40;

        if (bgOrb1) bgOrb1.style.transform = `translate(${x}px, ${y}px)`;
        if (bgOrb2) bgOrb2.style.transform = `translate(${-x}px, ${-y}px)`;
    });

    // Initial State Set
    updateState('focus', true);
});
