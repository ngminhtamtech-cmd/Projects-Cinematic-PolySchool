import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const Seat3D = require('../../src/main/webapp/assets/js/cinema-seat-3d.js');

test('trailer URLs are converted to muted autoplay YouTube embeds', () => {
    const id = 'AbCdEf12345';
    assert.equal(Seat3D.youtubeId(`https://youtu.be/${id}`), id);
    assert.equal(Seat3D.youtubeId(`https://www.youtube.com/watch?v=${id}&t=12`), id);
    assert.equal(Seat3D.youtubeId(`https://www.youtube.com/embed/${id}`), id);
    assert.equal(Seat3D.youtubeId(`https://www.youtube.com/shorts/${id}`), id);
    assert.match(Seat3D.trailerEmbedUrl(`https://youtu.be/${id}`), new RegExp(`/embed/${id}\\?autoplay=1&mute=1`));
    assert.equal(Seat3D.trailerEmbedUrl('https://example.com/trailer.mp4'), '');
});

test('seat normalization preserves position and identifies unavailable seats', () => {
    assert.deepEqual(Seat3D.normalizeSeat({
        seatKey: 'g7', rowLabel: 'g', seatNumber: '7', seatType: 'VIP', viewerState: 'heldByOther'
    }), {
        seatKey: 'g7', rowLabel: 'G', seatNumber: 7, seatType: 'vip', occupied: true
    });
    assert.equal(Seat3D.normalizeSeat({seatKey: 'A1', rowLabel: 'A', seatNumber: 1, selectable: false}).occupied, true);
    assert.equal(Seat3D.normalizeSeat({seatKey: 'A2', rowLabel: 'A', seatNumber: 2}).occupied, false);
});

test('row index is deterministic for camera depth', () => {
    assert.equal(Seat3D.rowIndex('A'), 0);
    assert.equal(Seat3D.rowIndex('l'), 11);
    assert.equal(Seat3D.rowIndex('AA'), -1);
});

test('theatre layout places near-screen rows first and expands toward the viewer', () => {
    const seats = [];
    for (const rowLabel of ['A', 'B', 'C']) {
        for (let seatNumber = 1; seatNumber <= 4; seatNumber += 1) {
            seats.push({seatKey: `${rowLabel}${seatNumber}`, rowLabel, seatNumber});
        }
    }
    const layout = Seat3D.buildTheatreLayout(seats, seats[1]);
    assert.deepEqual(layout.rows, ['C', 'B', 'A']);
    const screenRow = layout.chairs.find((chair) => chair.seat.seatKey === 'C1');
    const viewerRow = layout.chairs.find((chair) => chair.seat.seatKey === 'A1');
    assert.ok(screenRow.y < viewerRow.y);
    assert.ok(screenRow.scale < viewerRow.scale);
    assert.equal(layout.chairs.filter((chair) => chair.selected).length, 1);
});

test('theatre layout preserves a center aisle and clamps desktop camera values', () => {
    const seats = Array.from({length: 12}, (_, index) => ({
        seatKey: `J${index + 1}`,
        rowLabel: 'J',
        seatNumber: index + 1,
        seatType: index > 5 ? 'VIP' : 'standard'
    }));
    const layout = Seat3D.buildTheatreLayout(seats, seats[8]);
    const left = layout.chairs.find((chair) => chair.seat.seatNumber === 6);
    const right = layout.chairs.find((chair) => chair.seat.seatNumber === 7);
    assert.ok(right.x - left.x > 60, 'the center aisle must be wider than a regular chair step');
    assert.equal(Seat3D.clamp(20, -7, 7), 7);
    assert.equal(Seat3D.clamp(-20, -7, 7), -7);
});

const VIEWPORT = {w: 1500, h: 760};

function auditorium(rowCount, columnCount) {
    const rows = 'ABCDEFGHIJKLMNOPQRST'.split('').slice(0, rowCount).reverse();
    const seats = [];
    for (const rowLabel of rows) {
        for (let seatNumber = 1; seatNumber <= columnCount; seatNumber += 1) {
            seats.push({seatKey: `${rowLabel}${seatNumber}`, rowLabel, seatNumber});
        }
    }
    return seats;
}

function orbitCamera(preset) {
    const rad = Math.PI / 180;
    return {
        x: preset.target.x + preset.radius * Math.cos(preset.pitch * rad) * Math.sin(preset.yaw * rad),
        y: preset.target.y - preset.radius * Math.sin(preset.pitch * rad),
        z: preset.target.z + preset.radius * Math.cos(preset.pitch * rad) * Math.cos(preset.yaw * rad),
        yaw: preset.yaw,
        pitch: preset.pitch,
        perspective: preset.perspective
    };
}

function seatCamera(preset) {
    return {
        x: preset.eye.x, y: preset.eye.y, z: preset.eye.z,
        yaw: preset.yaw, pitch: preset.pitch, perspective: preset.perspective
    };
}

test('the floor is raked so every row sits above the row in front of it', () => {
    const layout = Seat3D.buildTheatreLayout(auditorium(3, 4), {seatKey: 'C1', rowLabel: 'C', seatNumber: 1});
    const [front, middle, back] = ['C1', 'B1', 'A1']
        .map((key) => layout.chairs.find((chair) => chair.seat.seatKey === key));
    assert.equal(front.elevation, 0, 'the row nearest the screen is the datum');
    assert.ok(middle.elevation > front.elevation && back.elevation > middle.elevation);
    // The floor plane must pass through the chairs it carries.
    assert.equal(Seat3D.floorElevationAt(front.z), front.elevation);
    assert.equal(Math.round(Seat3D.floorElevationAt(back.z)), back.elevation);
    assert.ok(Seat3D.floorElevationAt(0) < 0, 'the floor keeps dropping toward the screen');
});

test('the seat camera is placed at the eye of the chosen seat, not at a fixed spot', () => {
    const seats = auditorium(14, 12);
    for (const seatKey of ['N1', 'G7', 'A12']) {
        const chosen = seats.find((seat) => seat.seatKey === seatKey);
        const layout = Seat3D.buildTheatreLayout(seats, chosen);
        const chair = layout.chairs.find((item) => item.selected);
        const camera = seatCamera(Seat3D.buildCameraPresets(layout, VIEWPORT).seat);
        assert.equal(camera.x, chair.x, `${seatKey} must look from its own column`);
        assert.equal(camera.z, chair.z, `${seatKey} must look from its own row`);
        assert.ok(camera.y < -chair.elevation, `${seatKey} must look from above its own floor`);
    }
});

test('an off-centre seat sees a keystoned screen while the middle seat sees it square', () => {
    const seats = auditorium(14, 11);
    const layout = Seat3D.buildTheatreLayout(seats, seats.find((seat) => seat.seatKey === 'G6'));
    const middle = layout.chairs.find((item) => item.selected);
    assert.equal(middle.x, 0, 'seat 6 of 11 is the middle column');

    const screen = layout.screen;
    const edges = (camera) => [
        Seat3D.projectPoint(camera, {x: -screen.width / 2, y: -screen.centerElevation, z: 0}),
        Seat3D.projectPoint(camera, {x: screen.width / 2, y: -screen.centerElevation, z: 0})
    ];
    const skew = (camera) => {
        const [left, right] = edges(camera);
        return Math.max(left.distance, right.distance) / Math.min(left.distance, right.distance);
    };

    const centred = seatCamera(Seat3D.buildCameraPresets(layout, VIEWPORT).seat);
    assert.ok(Math.abs(centred.yaw) < 1e-9, 'the middle seat faces the screen head on');
    assert.ok(skew(centred) < 1.0001, 'the middle seat sees both screen edges equally far');

    const corner = Seat3D.buildTheatreLayout(seats, seats.find((seat) => seat.seatKey === 'N11'));
    const cornerCamera = seatCamera(Seat3D.buildCameraPresets(corner, VIEWPORT).seat);
    assert.ok(cornerCamera.yaw > 5, 'a seat on the right must turn left toward the screen');
    assert.ok(cornerCamera.pitch < -10, 'the front row must look up at the screen');
    assert.ok(skew(cornerCamera) > 1.2, 'the far screen edge must be measurably farther away');
});

test('the overview camera looks down on the room from behind the last row', () => {
    const seats = auditorium(14, 12);
    const layout = Seat3D.buildTheatreLayout(seats, seats.find((seat) => seat.seatKey === 'G7'));
    const camera = orbitCamera(Seat3D.buildCameraPresets(layout, VIEWPORT).overview);
    assert.ok(camera.z > layout.lastRowZ, 'the camera sits behind the last row');
    assert.ok(-camera.y > layout.maxElevation + 400, 'the camera sits well above the seating');
    assert.ok(camera.pitch > 0, 'the camera tilts downward');
    assert.equal(camera.yaw, 0, 'the default overview is square to the room');
});

test('every overview frames the whole room with the screen above every chair', () => {
    const halfW = VIEWPORT.w / 2;
    const halfH = VIEWPORT.h / 2;
    for (const [rowCount, columnCount] of [[14, 12], [6, 12], [3, 20], [20, 16], [1, 8]]) {
        const seats = auditorium(rowCount, columnCount);
        for (const index of [0, Math.floor(seats.length / 2), seats.length - 1]) {
            const layout = Seat3D.buildTheatreLayout(seats, seats[index]);
            const camera = orbitCamera(Seat3D.buildCameraPresets(layout, VIEWPORT).overview);
            const shape = `${rowCount}x${columnCount} seat ${seats[index].seatKey}`;
            const screenTop = Seat3D.projectPoint(camera,
                {x: 0, y: -(layout.screen.bottom + layout.screen.height), z: 0});
            const screenBottom = Seat3D.projectPoint(camera, {x: 0, y: -layout.screen.bottom, z: 0});
            assert.ok(screenTop.py >= -halfH, `${shape}: the screen must stay inside the frame`);

            let highestChair = Infinity;
            for (const chair of layout.chairs) {
                // Check both the feet and the top of the backrest: a chair whose
                // base falls past the bottom edge is not actually on screen.
                for (const height of [0, layout.chairHeight]) {
                    const point = Seat3D.projectPoint(camera,
                        {x: chair.x, y: -(chair.elevation + height), z: chair.z});
                    assert.ok(Math.abs(point.px) <= halfW + 1, `${shape}: chair ${chair.seat.seatKey} is off-frame`);
                    assert.ok(Math.abs(point.py) <= halfH + 1, `${shape}: chair ${chair.seat.seatKey} is off-frame`);
                    highestChair = Math.min(highestChair, point.py);
                }
            }
            assert.ok(screenBottom.py < highestChair,
                `${shape}: chairs must never cover the trailer`);
        }
    }
});

test('the camera transform applies the projection push, both rotations, then the eye offset', () => {
    const transform = Seat3D.cameraTransform({
        x: 10, y: -20, z: 30, yaw: 5, pitch: 12, perspective: 1400
    });
    assert.match(transform, /^translateZ\(1400\.0px\) rotateX\(-12\.000deg\) rotateY\(-5\.000deg\) translate3d\(-10\.0px, 20\.0px, -30\.0px\)$/);
});
