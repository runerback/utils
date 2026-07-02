const canvas = document.getElementById('waveform');
const ctx = canvas.getContext('2d');
const statusEl = document.getElementById('status');
const nodesEl = document.getElementById('nodes');
const toggleBtn = document.getElementById('toggle');

const colors = {
    gain: '#4caf50',
};

let ws = null;
let latestWaveform = [];
let isRunning = false;
let reconnectTimer = null;

function resizeCanvas() {
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * window.devicePixelRatio;
    canvas.height = rect.height * window.devicePixelRatio;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
}

window.addEventListener('resize', resizeCanvas);
resizeCanvas();

async function loadNodes() {
    const res = await fetch('/api/nodes');
    const data = await res.json();
    nodesEl.innerHTML = '';
    for (const node of data.nodes) {
        const locked = node.locked === true;
        const card = document.createElement('div');
        card.className = 'node';
        card.innerHTML = `
            <span class="node-name">${node.name}</span>
            <label class="switch">
                <input type="checkbox" data-node="${node.id}" ${locked ? 'disabled checked' : 'checked'}>
                <span class="slider"></span>
            </label>
        `;
        nodesEl.appendChild(card);
    }
    document.querySelectorAll('.node input:not([disabled])').forEach(input => {
        input.addEventListener('change', () => {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ type: 'toggle', node: input.dataset.node }));
            }
        });
    });
}

function updateSwitches(config) {
    document.querySelectorAll('.node input').forEach(input => {
        if (input.disabled) return;
        const key = `bypass_${input.dataset.node}`;
        input.checked = config[key] !== true;
    });
}

function draw() {
    const width = canvas.width / window.devicePixelRatio;
    const height = canvas.height / window.devicePixelRatio;
    ctx.clearRect(0, 0, width, height);

    ctx.strokeStyle = '#333';
    ctx.beginPath();
    ctx.moveTo(0, height / 2);
    ctx.lineTo(width, height / 2);
    ctx.stroke();

    if (!latestWaveform || latestWaveform.length === 0) return;

    const maxAbs = Math.max(...latestWaveform.map(Math.abs), 0.001);
    const amplitude = (height * 0.45) / maxAbs;
    const step = width / (latestWaveform.length - 1);

    ctx.strokeStyle = colors.gain || '#fff';
    ctx.lineWidth = 2;
    ctx.beginPath();
    for (let i = 0; i < latestWaveform.length; i++) {
        const x = i * step;
        const y = height / 2 - latestWaveform[i] * amplitude;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
    }
    ctx.stroke();
}

function setRunning(running) {
    isRunning = running;
    toggleBtn.textContent = running ? 'Stop' : 'Start';
    toggleBtn.classList.toggle('stopped', !running);
    statusEl.textContent = running ? 'Connecting...' : 'Stopped';
    statusEl.className = 'status disconnected';
}

function connect() {
    if (ws || !isRunning) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    ws = new WebSocket(`${protocol}//${window.location.host}/ws`);

    ws.onopen = () => {
        statusEl.textContent = 'Connected';
        statusEl.className = 'status connected';
    };

    ws.onclose = () => {
        ws = null;
        if (isRunning) {
            statusEl.textContent = 'Disconnected - reconnecting...';
            statusEl.className = 'status disconnected';
            reconnectTimer = setTimeout(connect, 1000);
        } else {
            statusEl.textContent = 'Stopped';
            statusEl.className = 'status disconnected';
        }
    };

    ws.onerror = () => {
        statusEl.textContent = 'Connection error';
        statusEl.className = 'status disconnected';
    };

    ws.onmessage = event => {
        const msg = JSON.parse(event.data);
        if (msg.type === 'state') {
            latestWaveform = (msg.waveforms || {}).gain || [];
            updateSwitches(msg.config || {});
        }
    };
}

function disconnect() {
    if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }
    if (ws) {
        const socket = ws;
        ws = null;
        socket.close();
    }
}

toggleBtn.addEventListener('click', () => {
    if (isRunning) {
        setRunning(false);
        disconnect();
    } else {
        setRunning(true);
        connect();
    }
});

function animate() {
    draw();
    requestAnimationFrame(animate);
}

loadNodes().then(() => {
    setRunning(false);
});
animate();
