(function (root, factory) {
    'use strict';
    var api = factory(root);
    if (typeof module === 'object' && module.exports) module.exports = api;
    if (root) root.CineBookSeat3D = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function (root) {
    'use strict';

    var DEG = Math.PI / 180;

    // World space is a real room, not a painted backdrop.  X grows to the
    // right, Z grows from the screen plane (Z = 0) toward the back of the
    // auditorium, and elevation grows upward from the first seated row.  CSS
    // keeps its Y axis pointing down, so every elevation is negated on the way
    // into a transform.  The camera is a matrix over this world, which is what
    // makes the two modes honest: the overview orbits above the room and the
    // seat mode puts the eye exactly where the ticket puts the moviegoer.
    var GEO = {
        seatPitch: 84,
        aisleGap: 118,
        rowPitch: 152,
        firstRowZ: 860,
        // The rake decides how much of the rows ahead a seated viewer sees: the
        // tops of the seat backs run down a line of atan(riser / rowPitch), so a
        // steeper rake pushes them out of frame entirely.
        riser: 22,
        eyeHeight: 116,
        chairWidth: 66,
        chairHeight: 96,
        screenWidth: 1260,
        screenBottom: 230,
        ceiling: 1290,
        sideMargin: 360,
        backMargin: 560,
        // Overview framing.  The camera hangs behind and above the last row and
        // looks down on it at overviewNearAngle; the focal length then follows
        // from the vertical angle it has to cover, which is the only way a room
        // this deep fits a landscape frame without going flat.
        overviewNearAngle: 33,
        overviewBackRatio: 0.46,
        overviewBackMin: 720,
        overviewTopMargin: 0.92,
        overviewBottomMargin: 1,
        overviewSideMargin: 0.96,
        // Seat framing.  The lens follows the vertical angle between the top of
        // the screen and the backs of the row in front, so every seat shows the
        // whole trailer plus enough foreground to feel seated.  It is clamped
        // around a nominal lens so a back row still looks farther than a front
        // row instead of being zoomed back to the same size.
        seatTopMargin: 0.94,
        seatBottomMargin: 0.8,
        seatSideMargin: 0.94,
        seatLensNominal: 1.02,
        seatLensFloor: 0.48,
        seatLensCeiling: 1.12
    };

    var LIMITS = {
        overviewYaw: 52,
        overviewPitchMin: 4,
        overviewPitchMax: 68,
        overviewDollyMin: 0.55,
        overviewDollyMax: 2.4,
        seatYaw: 34,
        seatPitch: 20,
        seatLensMin: 0.8,
        seatLensMax: 2.8
    };

    var FLIGHT_MS = 760;
    var LITE_DETAIL_SEATS = 240;

    var modal = null;
    var refs = {};
    var options = null;
    var layout = null;
    var presets = null;
    var chairViews = [];
    var currentCamera = null;
    var returnFocus = null;
    var requestId = 0;
    var filmsCache = new Map();
    var activeView = 'overview';
    var dragSession = null;
    var flightFrame = 0;
    var resizeFrame = 0;
    var motion = {
        overview: {yaw: 0, pitch: 0, dolly: 1},
        seat: {yaw: 0, pitch: 0, dolly: 1}
    };

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function rowIndex(label) {
        var value = String(label || '').trim().toUpperCase();
        return value.length === 1 ? value.charCodeAt(0) - 65 : -1;
    }

    function youtubeId(url) {
        var value = String(url || '').trim();
        if (!value) return '';
        var patterns = [
            /youtu\.be\/([a-zA-Z0-9_-]{6,})/,
            /youtube\.com\/watch\?[^#]*v=([a-zA-Z0-9_-]{6,})/,
            /youtube\.com\/embed\/([a-zA-Z0-9_-]{6,})/,
            /youtube\.com\/shorts\/([a-zA-Z0-9_-]{6,})/
        ];
        for (var index = 0; index < patterns.length; index += 1) {
            var match = value.match(patterns[index]);
            if (match) return match[1];
        }
        return '';
    }

    function trailerEmbedUrl(url) {
        var id = youtubeId(url);
        return id ? 'https://www.youtube.com/embed/' + encodeURIComponent(id)
                + '?autoplay=1&mute=1&playsinline=1&rel=0&modestbranding=1' : '';
    }

    function normalizeSeat(raw) {
        var viewerState = String(raw && raw.viewerState || '').toLowerCase();
        var status = String(raw && raw.status || '').toLowerCase();
        var seatType = String(raw && raw.seatType || 'standard').toLowerCase();
        if (seatType !== 'vip' && seatType !== 'couple') seatType = 'standard';
        return {
            seatKey: String(raw && raw.seatKey || ''),
            rowLabel: String(raw && raw.rowLabel || '').toUpperCase(),
            seatNumber: Number(raw && raw.seatNumber || 0),
            seatType: seatType,
            occupied: !!(raw && (raw.occupied || raw.isMaintenance || raw.selectable === false
                    || status === 'booked' || status === 'held' || status === 'maintenance'
                    || viewerState === 'booked' || viewerState === 'heldbyother'))
        };
    }

    function normalizeOptions(input) {
        var seats = (input && input.seats || []).map(normalizeSeat).filter(function (seat) {
            return seat.seatKey && rowIndex(seat.rowLabel) >= 0 && seat.seatNumber > 0;
        });
        var selected = normalizeSeat(input && input.seat || {});
        return {
            contextPath: String(input && input.contextPath || ''),
            filmId: Number(input && input.filmId || 0),
            filmTitle: String(input && input.filmTitle || 'Màn hình CineBook'),
            trailerUrl: String(input && input.trailerUrl || ''),
            seat: selected,
            seats: seats,
            sourceElement: input && input.sourceElement || null,
            allowFilmPicker: input && input.allowFilmPicker !== false
        };
    }

    // The floor is raked: every row sits one riser higher than the row in
    // front of it, and the plane keeps going past the first row so the area
    // under the screen drops away exactly like a real auditorium.
    function floorElevationAt(depthZ) {
        return (depthZ - GEO.firstRowZ) * GEO.riser / GEO.rowPitch;
    }

    function buildTheatreLayout(rawSeats, rawSelected) {
        var seats = (rawSeats || []).map(normalizeSeat).filter(function (seat) {
            return seat.seatKey && rowIndex(seat.rowLabel) >= 0 && seat.seatNumber > 0;
        });
        var selected = normalizeSeat(rawSelected || {});
        if (!seats.length && selected.seatKey) seats.push(selected);
        var rows = Array.from(new Set(seats.map(function (seat) { return seat.rowLabel; })))
                .sort(function (a, b) { return rowIndex(b) - rowIndex(a); });
        var minCol = seats.reduce(function (min, seat) { return Math.min(min, seat.seatNumber); }, Infinity);
        var maxCol = seats.reduce(function (max, seat) { return Math.max(max, seat.seatNumber); }, 0);
        if (!Number.isFinite(minCol)) minCol = 1;
        if (!maxCol) maxCol = minCol;
        var center = (minCol + maxCol) / 2;
        var rowDivisor = Math.max(1, rows.length - 1);
        var halfWidth = 0;
        var chairs = seats.map(function (seat) {
            var depth = Math.max(0, rows.indexOf(seat.rowLabel));
            var depthRatio = depth / rowDivisor;
            var offset = seat.seatNumber - center;
            var aisle = offset === 0 ? 0 : (offset > 0 ? 1 : -1) * GEO.aisleGap / 2;
            var x = offset * GEO.seatPitch + aisle;
            var z = GEO.firstRowZ + depth * GEO.rowPitch;
            halfWidth = Math.max(halfWidth, Math.abs(x));
            return {
                seat: seat,
                rowDepth: depth,
                depthRatio: depthRatio,
                selected: seat.seatKey === selected.seatKey,
                // x and y are the floor-plan coordinates of the chair: x across
                // the room, y along the room measured from the screen.  z is the
                // same depth under the name the 3D transforms use.
                x: x,
                y: z,
                z: z,
                elevation: depth * GEO.riser,
                scale: 0.72 + depthRatio * 0.46,
                rotation: clamp(offset * 0.85, -9, 9)
            };
        });
        var selectedDepth = Math.max(0, rows.indexOf(selected.rowLabel));
        var selectedChair = null;
        for (var index = 0; index < chairs.length; index += 1) {
            if (chairs[index].selected) selectedChair = chairs[index];
        }
        var screenHeight = Math.round(GEO.screenWidth * 9 / 16);
        var lastRowZ = GEO.firstRowZ + Math.max(0, rows.length - 1) * GEO.rowPitch;
        var eyeZ = selectedChair ? selectedChair.z : GEO.firstRowZ + selectedDepth * GEO.rowPitch;
        var eyeElevation = (selectedChair ? selectedChair.elevation : selectedDepth * GEO.riser) + GEO.eyeHeight;
        return {
            rows: rows,
            minCol: minCol,
            maxCol: maxCol,
            center: center,
            chairs: chairs,
            selectedDepth: selectedDepth,
            selectedOffset: selected.seatNumber - center,
            halfWidth: halfWidth,
            chairHeight: GEO.chairHeight,
            lastRowZ: lastRowZ,
            maxElevation: Math.max(0, rows.length - 1) * GEO.riser,
            roomHalfWidth: Math.max(halfWidth + GEO.chairWidth, GEO.screenWidth / 2) + GEO.sideMargin,
            roomBackZ: lastRowZ + GEO.backMargin,
            screen: {
                width: GEO.screenWidth,
                height: screenHeight,
                bottom: GEO.screenBottom,
                centerElevation: GEO.screenBottom + screenHeight / 2
            },
            eye: {x: selectedChair ? selectedChair.x : 0, y: -eyeElevation, z: eyeZ}
        };
    }

    // Rotate a world delta into camera space: R_x(-pitch) * R_y(-yaw) * delta.
    // Camera space looks down its own -Z, which is what the CSS perspective
    // projection consumes once the world is pushed back by translateZ.
    function toCameraSpace(dx, dy, dz, yawDeg, pitchDeg) {
        var yaw = yawDeg * DEG;
        var pitch = pitchDeg * DEG;
        var x = dx * Math.cos(yaw) - dz * Math.sin(yaw);
        var forward = dx * Math.sin(yaw) + dz * Math.cos(yaw);
        return {
            x: x,
            y: dy * Math.cos(pitch) + forward * Math.sin(pitch),
            z: forward * Math.cos(pitch) - dy * Math.sin(pitch)
        };
    }

    function projectPoint(camera, point) {
        var local = toCameraSpace(point.x - camera.x, point.y - camera.y, point.z - camera.z,
                camera.yaw, camera.pitch);
        var distance = -local.z;
        if (distance <= 1) return {px: 0, py: 0, distance: distance, behind: true};
        return {
            px: camera.perspective * local.x / distance,
            py: camera.perspective * local.y / distance,
            distance: distance,
            behind: false
        };
    }

    // Split a vertical angular span so its far edge lands at topMargin of the
    // half-frame and its near edge at bottomMargin.  Solved by bisection
    // because tan() makes the split non-linear.
    function splitFrameSpan(span, topMargin, bottomMargin) {
        var low = 1e-4;
        var high = span - 1e-4;
        for (var step = 0; step < 48; step += 1) {
            var mid = (low + high) / 2;
            if (Math.tan(mid) * bottomMargin > Math.tan(span - mid) * topMargin) high = mid;
            else low = mid;
        }
        return (low + high) / 2;
    }

    function buildOverviewCamera(theatre, size) {
        var screen = theatre.screen;
        var halfH = size.h * 0.5;
        var halfW = size.w * 0.5;
        var nearTop = theatre.maxElevation + GEO.chairHeight;
        var back = Math.max(GEO.overviewBackMin, GEO.overviewBackRatio * theatre.lastRowZ);
        var elevation = nearTop + back * Math.tan(GEO.overviewNearAngle * DEG);
        var camZ = theatre.lastRowZ + back;
        var thetaTop = Math.atan((elevation - (screen.bottom + screen.height)) / camZ);
        // Frame down to the FEET of the last row, not the top of its backrests,
        // or the nearest chairs hang off the bottom edge with only their tops
        // clipped into view.
        var thetaNear = Math.atan((elevation - theatre.maxElevation) / back);
        var span = Math.max(10 * DEG, thetaNear - thetaTop);
        var above = splitFrameSpan(span, GEO.overviewTopMargin, GEO.overviewBottomMargin);
        var pitch = thetaTop + above;
        // The widest chair of the nearest row decides the horizontal budget.
        // Project it for real rather than using its straight-line distance: the
        // tilted axis shortens the depth and a wide, shallow room overflows.
        var nearHalf = theatre.halfWidth + GEO.chairWidth / 2;
        var nearCorner = toCameraSpace(nearHalf, -nearTop + elevation, theatre.lastRowZ - camZ,
                0, pitch / DEG);
        var perspective = Math.min(
                halfH * GEO.overviewTopMargin / Math.tan(above),
                halfW * GEO.overviewSideMargin * (-nearCorner.z) / Math.max(1, Math.abs(nearCorner.x)));
        // Anchor the orbit on a pivot that already sits on the camera axis, so
        // dragging with zero offset reproduces exactly this framing.
        var radius = Math.abs(camZ - theatre.lastRowZ * 0.35) / Math.cos(pitch);
        return {
            target: {
                x: 0,
                y: -elevation + radius * Math.sin(pitch),
                z: camZ - radius * Math.cos(pitch)
            },
            yaw: 0,
            pitch: pitch / DEG,
            radius: radius,
            perspective: perspective
        };
    }

    // The eye never moves off the chosen seat: only the lens and the tilt are
    // framed, so the angle onto the screen -- the whole point of this view --
    // stays exactly what that ticket buys.
    function buildSeatCamera(theatre, size) {
        var screen = theatre.screen;
        var eye = theatre.eye;
        var halfH = size.h * 0.5;
        var halfW = size.w * 0.5;
        var yaw = Math.atan2(eye.x, eye.z) / DEG;
        var reach = Math.max(1, Math.sqrt(eye.x * eye.x + eye.z * eye.z));
        var eyeElevation = -eye.y;
        var angleTop = Math.atan((screen.bottom + screen.height - eyeElevation) / reach);
        var angleBottom = Math.atan((screen.bottom - eyeElevation) / reach);
        if (theatre.selectedDepth > 0) {
            var aheadTop = (theatre.selectedDepth - 1) * GEO.riser + GEO.chairHeight;
            angleBottom = Math.min(angleBottom, Math.atan((aheadTop - eyeElevation) / GEO.rowPitch));
        }
        var span = Math.max(10 * DEG, angleTop - angleBottom);
        var above = splitFrameSpan(span, GEO.seatTopMargin, GEO.seatBottomMargin);
        var pitch = -(angleTop - above);
        var perspective = halfH * GEO.seatTopMargin / Math.tan(above);
        // A seat off to the side keystones the screen, throwing its far corners
        // well past the edges the centre-line span accounted for.  Project all
        // four and pull the lens back by however much the worst one overshoots.
        var overshoot = 1;
        var xs = [-screen.width / 2, screen.width / 2];
        var ys = [-screen.bottom, -(screen.bottom + screen.height)];
        for (var a = 0; a < 2; a += 1) {
            for (var b = 0; b < 2; b += 1) {
                var corner = toCameraSpace(xs[a] - eye.x, ys[b] - eye.y, 0 - eye.z,
                        yaw, pitch / DEG);
                var depth = Math.max(1, -corner.z);
                overshoot = Math.max(overshoot,
                        perspective * Math.abs(corner.x) / (depth * halfW * GEO.seatSideMargin),
                        perspective * Math.abs(corner.y) / (depth * halfH * GEO.seatTopMargin));
            }
        }
        perspective /= overshoot;
        var nominal = size.w * GEO.seatLensNominal;
        return {
            eye: eye,
            yaw: yaw,
            pitch: pitch / DEG,
            perspective: clamp(perspective, nominal * GEO.seatLensFloor, nominal * GEO.seatLensCeiling)
        };
    }

    function buildCameraPresets(theatre, size) {
        return {
            overview: buildOverviewCamera(theatre, size),
            seat: buildSeatCamera(theatre, size)
        };
    }

    function resolveCamera(view) {
        if (view === 'seat') {
            var seatBase = presets.seat;
            var seatMotion = motion.seat;
            return {
                x: seatBase.eye.x,
                y: seatBase.eye.y,
                z: seatBase.eye.z,
                yaw: seatBase.yaw + clamp(seatMotion.yaw, -LIMITS.seatYaw, LIMITS.seatYaw),
                pitch: seatBase.pitch + clamp(seatMotion.pitch, -LIMITS.seatPitch, LIMITS.seatPitch),
                perspective: seatBase.perspective
                        * clamp(seatMotion.dolly, LIMITS.seatLensMin, LIMITS.seatLensMax)
            };
        }
        var base = presets.overview;
        var free = motion.overview;
        var pitch = clamp(base.pitch + free.pitch, LIMITS.overviewPitchMin, LIMITS.overviewPitchMax);
        var yaw = clamp(free.yaw, -LIMITS.overviewYaw, LIMITS.overviewYaw);
        var radius = base.radius / clamp(free.dolly, LIMITS.overviewDollyMin, LIMITS.overviewDollyMax);
        return {
            x: base.target.x + radius * Math.cos(pitch * DEG) * Math.sin(yaw * DEG),
            y: base.target.y - radius * Math.sin(pitch * DEG),
            z: base.target.z + radius * Math.cos(pitch * DEG) * Math.cos(yaw * DEG),
            yaw: yaw,
            pitch: pitch,
            perspective: base.perspective
        };
    }

    function cameraTransform(camera) {
        return 'translateZ(' + camera.perspective.toFixed(1) + 'px)'
                + ' rotateX(' + (-camera.pitch).toFixed(3) + 'deg)'
                + ' rotateY(' + (-camera.yaw).toFixed(3) + 'deg)'
                + ' translate3d(' + (-camera.x).toFixed(1) + 'px, '
                + (-camera.y).toFixed(1) + 'px, ' + (-camera.z).toFixed(1) + 'px)';
    }

    function createElement(tag, className, text) {
        var element = document.createElement(tag);
        if (className) element.className = className;
        if (text != null) element.textContent = text;
        return element;
    }

    function seatTypeLabel(type) {
        if (type === 'vip') return 'VIP';
        if (type === 'couple') return 'Ghế đôi';
        return 'Ghế thường';
    }

    function prefersReducedMotion() {
        return !!(root.matchMedia && root.matchMedia('(prefers-reduced-motion: reduce)').matches);
    }

    function viewportSize() {
        var rect = refs.viewport.getBoundingClientRect();
        return {w: Math.max(360, rect.width), h: Math.max(260, rect.height)};
    }

    function ensureModal() {
        if (modal || !root.document) return modal;
        modal = createElement('div', 'cb-seat3d');
        modal.id = 'cinebookSeat3d';
        modal.setAttribute('aria-hidden', 'true');
        modal.innerHTML = [
            '<div class="cb-seat3d__shell" role="dialog" aria-modal="true" aria-labelledby="cbSeat3dTitle" tabindex="-1">',
            '  <header class="cb-seat3d__header">',
            '    <div class="cb-seat3d__brand"><span class="cb-seat3d__brand-mark">CINE</span><strong>BOOK 3D</strong></div>',
            '    <div class="cb-seat3d__identity"><span class="cb-seat3d__eyebrow">TRẢI NGHIỆM PHÒNG CHIẾU</span><h2 id="cbSeat3dTitle">Toàn cảnh phòng chiếu</h2><p id="cbSeat3dSubtitle"></p></div>',
            '    <p id="cbSeat3dInstruction" class="cb-seat3d__instruction">Kéo chuột để xoay camera · Lăn chuột để thu phóng</p>',
            '    <div class="cb-seat3d__header-actions">',
            '      <label class="cb-seat3d__film-field" for="cbSeat3dFilm"><span>Trailer trên màn hình</span><select id="cbSeat3dFilm" aria-label="Chọn trailer phim"></select></label>',
            '      <div class="cb-seat3d__modes" role="group" aria-label="Chế độ quan sát"><button type="button" data-view="overview" class="is-active" aria-pressed="true">Toàn cảnh</button><button type="button" data-view="seat" aria-pressed="false">Từ ghế</button></div>',
            '      <button type="button" class="cb-seat3d__reset" aria-label="Đặt lại góc nhìn" title="Đặt lại góc nhìn"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4v6h6M20 20v-6h-6M5.2 15a8 8 0 0 0 13.1 2.7M18.8 9A8 8 0 0 0 5.7 6.3"/></svg></button>',
            '      <button type="button" class="cb-seat3d__close" aria-label="Đóng xem ghế 3D"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button>',
            '    </div>',
            '  </header>',
            '  <main class="cb-seat3d__viewport" data-view="overview">',
            '    <div class="cb-seat3d__ambient" aria-hidden="true"></div>',
            '    <div class="cb-seat3d__stage" aria-hidden="true">',
            '      <div id="cbSeat3dWorld" class="cb-seat3d__world">',
            '        <div class="cb-seat3d__surface cb-seat3d__floor"><i class="cb-seat3d__aisle cb-seat3d__aisle--center"></i><i class="cb-seat3d__aisle cb-seat3d__aisle--left"></i><i class="cb-seat3d__aisle cb-seat3d__aisle--right"></i></div>',
            '        <div class="cb-seat3d__surface cb-seat3d__ceiling"><i></i><i></i><i></i><i></i><i></i></div>',
            '        <div class="cb-seat3d__surface cb-seat3d__wall cb-seat3d__wall--left"><i></i><i></i><i></i><i></i></div>',
            '        <div class="cb-seat3d__surface cb-seat3d__wall cb-seat3d__wall--right"><i></i><i></i><i></i><i></i></div>',
            '        <div class="cb-seat3d__surface cb-seat3d__backwall"></div>',
            '        <div class="cb-seat3d__surface cb-seat3d__apron"></div>',
            '        <div class="cb-seat3d__screen-stage">',
            '          <div class="cb-seat3d__screen-glow"></div>',
            '          <div class="cb-seat3d__screen-frame"><div class="cb-seat3d__screen"><iframe id="cbSeat3dTrailer" title="Trailer trên màn hình rạp" referrerpolicy="strict-origin-when-cross-origin" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe><div id="cbSeat3dPlaceholder" class="cb-seat3d__placeholder"><span>TRAILER ĐANG CHIẾU</span><strong id="cbSeat3dFilmTitle"></strong><small>Đang chuẩn bị trailer…</small></div></div></div>',
            '        </div>',
            '        <div id="cbSeat3dHalo" class="cb-seat3d__halo"></div>',
            '        <div id="cbSeat3dRowTags" class="cb-seat3d__rowtags"></div>',
            '        <div id="cbSeat3dSeats" class="cb-seat3d__seats"></div>',
            '      </div>',
            '    </div>',
            '    <div class="cb-seat3d__foreground" aria-hidden="true"></div>',
            '    <div class="cb-seat3d__grain" aria-hidden="true"></div>',
            '    <aside class="cb-seat3d__map" aria-label="Sơ đồ ghế trong phòng">',
            '      <div class="cb-seat3d__map-head"><span>SƠ ĐỒ GHẾ</span><strong id="cbSeat3dMapSeat"></strong></div>',
            '      <div class="cb-seat3d__map-screen">MÀN HÌNH</div><div id="cbSeat3dMapGrid" class="cb-seat3d__map-grid"></div>',
            '      <div class="cb-seat3d__map-key"><span><i></i>Trống</span><span><i></i>Đã đặt</span><span><i></i>Đang xem</span></div>',
            '      <button type="button" class="cb-seat3d__map-action"><span></span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></button>',
            '    </aside>',
            '    <div class="cb-seat3d__view-label" aria-live="polite"><i></i><div><span id="cbSeat3dViewLabel">TOÀN CẢNH</span><strong id="cbSeat3dPovLabel"></strong></div></div>',
            '    <div class="cb-seat3d__legend"><span><i class="cb-seat3d__legend-seat cb-seat3d__legend-seat--standard"></i>Thường</span><span><i class="cb-seat3d__legend-seat cb-seat3d__legend-seat--vip"></i>VIP</span><span><i class="cb-seat3d__legend-seat cb-seat3d__legend-seat--couple"></i>Đôi</span><span><i class="cb-seat3d__legend-seat cb-seat3d__legend-seat--selected"></i>Đang xem</span></div>',
            '    <button type="button" class="cb-seat3d__done">Quay lại chọn ghế</button>',
            '  </main>',
            '</div>'
        ].join('');
        document.body.appendChild(modal);
        refs.shell = modal.querySelector('.cb-seat3d__shell');
        refs.viewport = modal.querySelector('.cb-seat3d__viewport');
        refs.stage = modal.querySelector('.cb-seat3d__stage');
        refs.world = modal.querySelector('#cbSeat3dWorld');
        refs.floor = modal.querySelector('.cb-seat3d__floor');
        refs.ceiling = modal.querySelector('.cb-seat3d__ceiling');
        refs.wallLeft = modal.querySelector('.cb-seat3d__wall--left');
        refs.wallRight = modal.querySelector('.cb-seat3d__wall--right');
        refs.backwall = modal.querySelector('.cb-seat3d__backwall');
        refs.apron = modal.querySelector('.cb-seat3d__apron');
        refs.screenStage = modal.querySelector('.cb-seat3d__screen-stage');
        refs.seats = modal.querySelector('#cbSeat3dSeats');
        refs.rowTags = modal.querySelector('#cbSeat3dRowTags');
        refs.halo = modal.querySelector('#cbSeat3dHalo');
        refs.subtitle = modal.querySelector('#cbSeat3dSubtitle');
        refs.title = modal.querySelector('#cbSeat3dTitle');
        refs.instruction = modal.querySelector('#cbSeat3dInstruction');
        refs.pov = modal.querySelector('#cbSeat3dPovLabel');
        refs.viewLabel = modal.querySelector('#cbSeat3dViewLabel');
        refs.filmSelect = modal.querySelector('#cbSeat3dFilm');
        refs.iframe = modal.querySelector('#cbSeat3dTrailer');
        refs.placeholder = modal.querySelector('#cbSeat3dPlaceholder');
        refs.filmTitle = modal.querySelector('#cbSeat3dFilmTitle');
        refs.map = modal.querySelector('.cb-seat3d__map');
        refs.mapGrid = modal.querySelector('#cbSeat3dMapGrid');
        refs.mapSeat = modal.querySelector('#cbSeat3dMapSeat');
        refs.mapAction = modal.querySelector('.cb-seat3d__map-action');
        refs.reset = modal.querySelector('.cb-seat3d__reset');
        modal.querySelector('.cb-seat3d__close').addEventListener('click', close);
        modal.querySelector('.cb-seat3d__done').addEventListener('click', close);
        refs.reset.addEventListener('click', resetViewMotion);
        refs.mapAction.addEventListener('click', function () {
            setView(activeView === 'overview' ? 'seat' : 'overview');
        });
        modal.querySelectorAll('.cb-seat3d__modes [data-view]').forEach(function (button) {
            button.addEventListener('click', function () { setView(button.dataset.view); });
        });
        refs.filmSelect.addEventListener('change', function () {
            loadFilm(Number(refs.filmSelect.value));
        });
        refs.viewport.addEventListener('pointerdown', beginViewDrag);
        refs.viewport.addEventListener('pointermove', updateViewDrag);
        refs.viewport.addEventListener('pointerup', endViewDrag);
        refs.viewport.addEventListener('pointercancel', cancelViewDrag);
        refs.viewport.addEventListener('wheel', updateViewZoom, {passive: false});
        modal.addEventListener('click', function (event) {
            if (event.target === modal) close();
        });
        document.addEventListener('keydown', handleKeydown);
        root.addEventListener('resize', handleResize);
        return modal;
    }

    function handleResize() {
        if (!modal || !modal.classList.contains('is-open') || !layout) return;
        if (resizeFrame) return;
        resizeFrame = root.requestAnimationFrame(function () {
            resizeFrame = 0;
            presets = buildCameraPresets(layout, viewportSize());
            applyCamera(resolveCamera(activeView));
        });
    }

    function resetViewMotion() {
        motion[activeView] = {yaw: 0, pitch: 0, dolly: 1};
        flyTo(activeView, false);
    }

    function applyCamera(camera) {
        if (!refs.world) return;
        // The perspective must sit on the world's direct parent, otherwise the
        // projection never reaches the room.
        refs.stage.style.perspective = camera.perspective.toFixed(1) + 'px';
        refs.world.style.transform = cameraTransform(camera);
        currentCamera = camera;
    }

    function stopFlight() {
        if (!flightFrame) return;
        root.cancelAnimationFrame(flightFrame);
        flightFrame = 0;
    }

    // Flying between two camera states beats cutting: the moviegoer keeps their
    // bearings because they watch the room swing around into their own seat.
    function flyTo(view, applyDepthLast) {
        var target = resolveCamera(view);
        stopFlight();
        if (!currentCamera || prefersReducedMotion()) {
            applyCamera(target);
            applyViewDepth(view);
            return;
        }
        if (!applyDepthLast) applyViewDepth(view);
        var from = currentCamera;
        var started = 0;
        var step = function (time) {
            if (!started) started = time;
            var progress = clamp((time - started) / FLIGHT_MS, 0, 1);
            var eased = progress < 0.5
                    ? 4 * progress * progress * progress
                    : 1 - Math.pow(-2 * progress + 2, 3) / 2;
            applyCamera({
                x: from.x + (target.x - from.x) * eased,
                y: from.y + (target.y - from.y) * eased,
                z: from.z + (target.z - from.z) * eased,
                yaw: from.yaw + (target.yaw - from.yaw) * eased,
                pitch: from.pitch + (target.pitch - from.pitch) * eased,
                perspective: from.perspective + (target.perspective - from.perspective) * eased
            });
            if (progress < 1) {
                flightFrame = root.requestAnimationFrame(step);
                return;
            }
            flightFrame = 0;
            if (applyDepthLast) applyViewDepth(view);
        };
        flightFrame = root.requestAnimationFrame(step);
    }

    function interactiveTarget(target) {
        if (!target || typeof target.closest !== 'function') return null;
        return target.closest('button, select, iframe, .cb-seat3d__map');
    }

    function beginViewDrag(event) {
        if (event.button !== 0 || interactiveTarget(event.target)) return;
        var current = motion[activeView];
        dragSession = {
            pointerId: event.pointerId,
            startX: event.clientX,
            startY: event.clientY,
            originYaw: current.yaw,
            originPitch: current.pitch
        };
        stopFlight();
        refs.viewport.classList.add('is-dragging');
        refs.viewport.setPointerCapture(event.pointerId);
    }

    function updateViewDrag(event) {
        if (!dragSession || dragSession.pointerId !== event.pointerId) return;
        var dx = event.clientX - dragSession.startX;
        var dy = event.clientY - dragSession.startY;
        var current = motion[activeView];
        var yawSpeed = activeView === 'overview' ? 0.16 : 0.09;
        var pitchSpeed = activeView === 'overview' ? 0.11 : 0.07;
        if (activeView === 'overview') {
            current.yaw = clamp(dragSession.originYaw + dx * yawSpeed,
                    -LIMITS.overviewYaw, LIMITS.overviewYaw);
            current.pitch = clamp(dragSession.originPitch - dy * pitchSpeed, -24, 34);
        } else {
            current.yaw = clamp(dragSession.originYaw - dx * yawSpeed,
                    -LIMITS.seatYaw, LIMITS.seatYaw);
            current.pitch = clamp(dragSession.originPitch - dy * pitchSpeed,
                    -LIMITS.seatPitch, LIMITS.seatPitch);
        }
        applyCamera(resolveCamera(activeView));
    }

    function endViewDrag(event) {
        if (!dragSession || dragSession.pointerId !== event.pointerId) return;
        if (refs.viewport.hasPointerCapture(event.pointerId)) refs.viewport.releasePointerCapture(event.pointerId);
        dragSession = null;
        refs.viewport.classList.remove('is-dragging');
    }

    function cancelViewDrag() {
        dragSession = null;
        if (refs.viewport) refs.viewport.classList.remove('is-dragging');
    }

    function updateViewZoom(event) {
        if (interactiveTarget(event.target)) return;
        event.preventDefault();
        if (!presets) return;
        var current = motion[activeView];
        var min = activeView === 'overview' ? LIMITS.overviewDollyMin : LIMITS.seatLensMin;
        var max = activeView === 'overview' ? LIMITS.overviewDollyMax : LIMITS.seatLensMax;
        stopFlight();
        current.dolly = clamp(current.dolly * Math.exp(-event.deltaY * 0.0012), min, max);
        applyCamera(resolveCamera(activeView));
    }

    function nudgeCamera(yawStep, pitchStep, dollyStep) {
        if (!presets) return;
        var current = motion[activeView];
        var yawLimit = activeView === 'overview' ? LIMITS.overviewYaw : LIMITS.seatYaw;
        var pitchLimit = activeView === 'overview' ? 34 : LIMITS.seatPitch;
        var min = activeView === 'overview' ? LIMITS.overviewDollyMin : LIMITS.seatLensMin;
        var max = activeView === 'overview' ? LIMITS.overviewDollyMax : LIMITS.seatLensMax;
        stopFlight();
        current.yaw = clamp(current.yaw + yawStep, -yawLimit, yawLimit);
        current.pitch = clamp(current.pitch + pitchStep,
                activeView === 'overview' ? -24 : -pitchLimit, pitchLimit);
        current.dolly = clamp(current.dolly * dollyStep, min, max);
        applyCamera(resolveCamera(activeView));
    }

    function setView(view) {
        activeView = view === 'seat' ? 'seat' : 'overview';
        cancelViewDrag();
        refs.viewport.dataset.view = activeView;
        modal.querySelectorAll('.cb-seat3d__modes [data-view]').forEach(function (button) {
            var selected = button.dataset.view === activeView;
            button.classList.toggle('is-active', selected);
            button.setAttribute('aria-pressed', selected ? 'true' : 'false');
        });
        var seatKey = options && options.seat.seatKey || '';
        if (activeView === 'overview') {
            refs.title.textContent = 'Toàn cảnh phòng chiếu';
            refs.instruction.textContent = 'Kéo chuột để xoay quanh phòng · Lăn chuột để thu phóng';
            refs.viewLabel.textContent = 'TOÀN CẢNH';
            refs.pov.textContent = 'Camera trên cao nhìn xuống ghế ' + seatKey;
            refs.mapAction.querySelector('span').textContent = 'Đi tới góc nhìn ' + seatKey;
        } else {
            refs.title.textContent = 'Góc nhìn từ ghế ' + seatKey;
            refs.instruction.textContent = 'Camera đặt đúng tại mắt người ngồi ghế ' + seatKey;
            refs.viewLabel.textContent = 'TỪ GHẾ ' + seatKey;
            refs.pov.textContent = 'Hàng ' + options.seat.rowLabel + ' · nhìn thẳng lên màn hình';
            refs.mapAction.querySelector('span').textContent = 'Trở lại toàn cảnh';
        }
        flyTo(activeView, activeView === 'seat');
    }

    function placeSurface(element, width, height, transform) {
        element.style.width = width.toFixed(1) + 'px';
        element.style.height = height.toFixed(1) + 'px';
        element.style.marginLeft = (-width / 2).toFixed(1) + 'px';
        element.style.marginTop = (-height / 2).toFixed(1) + 'px';
        element.style.transform = transform;
    }

    // The room shell is rebuilt per view.  In seat mode everything behind the
    // eye is cut away, which keeps large planes from straddling the near plane
    // (where CSS 3D projection tears) and matches what a seated viewer sees.
    function applyViewDepth(view) {
        if (!layout) return;
        var seatView = view === 'seat';
        var backZ = seatView
                ? Math.max(GEO.firstRowZ * 0.5, layout.eye.z - 140)
                : layout.roomBackZ;
        var depth = Math.max(400, backZ);
        var midZ = backZ / 2;
        var slope = Math.atan2(GEO.riser, GEO.rowPitch) / DEG;
        var width = layout.roomHalfWidth * 2;
        var floorFrontY = -floorElevationAt(0);
        var floorBackY = -floorElevationAt(backZ);
        var floorMidY = (floorFrontY + floorBackY) / 2;

        placeSurface(refs.floor, width, depth / Math.cos(slope * DEG),
                'translate3d(0, ' + floorMidY.toFixed(1) + 'px, ' + midZ.toFixed(1) + 'px)'
                + ' rotateX(' + (90 + slope).toFixed(2) + 'deg)');
        refs.floor.style.setProperty('--aisle-half', (GEO.aisleGap / 2 / width * 100).toFixed(3) + '%');
        refs.floor.style.setProperty('--bank-half', ((layout.halfWidth + GEO.chairWidth * 0.7) / width * 100).toFixed(3) + '%');

        placeSurface(refs.ceiling, width, depth,
                'translate3d(0, ' + (-GEO.ceiling) + 'px, ' + midZ.toFixed(1) + 'px) rotateX(-90deg)');

        var wallTop = -GEO.ceiling;
        var wallBottom = floorFrontY + 90;
        var wallHeight = wallBottom - wallTop;
        var wallMidY = (wallTop + wallBottom) / 2;
        placeSurface(refs.wallLeft, depth, wallHeight,
                'translate3d(' + (-layout.roomHalfWidth) + 'px, ' + wallMidY.toFixed(1) + 'px, '
                + midZ.toFixed(1) + 'px) rotateY(90deg)');
        placeSurface(refs.wallRight, depth, wallHeight,
                'translate3d(' + layout.roomHalfWidth + 'px, ' + wallMidY.toFixed(1) + 'px, '
                + midZ.toFixed(1) + 'px) rotateY(-90deg)');

        refs.backwall.hidden = seatView;
        if (!seatView) {
            var backTop = -GEO.ceiling;
            var backBottom = floorBackY + 90;
            placeSurface(refs.backwall, width, backBottom - backTop,
                    'translate3d(0, ' + ((backTop + backBottom) / 2).toFixed(1) + 'px, '
                    + layout.roomBackZ.toFixed(1) + 'px) rotateY(180deg)');
        }

        var apronTop = -layout.screen.bottom;
        var apronHeight = floorFrontY - apronTop;
        placeSurface(refs.apron, width, apronHeight,
                'translate3d(0, ' + ((apronTop + floorFrontY) / 2).toFixed(1) + 'px, 0px)');

        for (var index = 0; index < chairViews.length; index += 1) {
            chairViews[index].element.classList.toggle('is-behind-camera',
                    seatView && chairViews[index].item.rowDepth >= layout.selectedDepth);
        }
    }

    function renderChair(item) {
        var seat = item.seat;
        var chair = createElement('div', 'cb-seat3d__chair cb-seat3d__chair--' + seat.seatType);
        chair.style.transform = 'translate3d(' + item.x.toFixed(1) + 'px, '
                + (-item.elevation).toFixed(1) + 'px, ' + item.z.toFixed(1) + 'px)'
                + ' rotateY(' + item.rotation.toFixed(2) + 'deg)';
        chair.title = 'Ghế ' + seat.seatKey + ' · ' + seatTypeLabel(seat.seatType);
        if (item.selected) chair.classList.add('is-selected');
        if (seat.occupied) chair.classList.add('is-occupied');
        chair.innerHTML = '<i class="cb-seat3d__chair-back"><b>' + seat.seatNumber
                + '</b></i><i class="cb-seat3d__chair-base"></i>';
        refs.seats.appendChild(chair);
        chairViews.push({element: chair, item: item});
    }

    function renderRowTags(theatre) {
        refs.rowTags.innerHTML = '';
        var tagX = -(theatre.halfWidth + 96);
        theatre.rows.forEach(function (label, depth) {
            var tag = createElement('i', 'cb-seat3d__rowtag', label);
            tag.style.transform = 'translate3d(' + tagX.toFixed(1) + 'px, '
                    + (-(depth * GEO.riser + 34)).toFixed(1) + 'px, '
                    + (GEO.firstRowZ + depth * GEO.rowPitch).toFixed(1) + 'px) rotateY(52deg)';
            refs.rowTags.appendChild(tag);
        });
    }

    function renderMap(theatre) {
        refs.mapGrid.innerHTML = '';
        var span = Math.max(1, theatre.maxCol - theatre.minCol + 1);
        refs.mapGrid.style.setProperty('--map-columns', String(span));
        var byPosition = new Map();
        theatre.chairs.forEach(function (item) {
            byPosition.set(item.seat.rowLabel + ':' + item.seat.seatNumber, item);
        });
        theatre.rows.forEach(function (row) {
            for (var number = theatre.minCol; number <= theatre.maxCol; number += 1) {
                var item = byPosition.get(row + ':' + number);
                var cell = createElement('i', 'cb-seat3d__map-seat');
                if (!item) {
                    cell.classList.add('is-empty');
                } else {
                    cell.classList.add('cb-seat3d__map-seat--' + item.seat.seatType);
                    if (item.seat.occupied) cell.classList.add('is-occupied');
                    if (item.selected) cell.classList.add('is-selected');
                    cell.title = 'Ghế ' + item.seat.seatKey;
                }
                refs.mapGrid.appendChild(cell);
            }
        });
        refs.mapSeat.textContent = options.seat.seatKey;
    }

    function renderScene() {
        refs.seats.innerHTML = '';
        chairViews = [];
        var seats = options.seats.length ? options.seats : [options.seat];
        layout = buildTheatreLayout(seats, options.seat);
        refs.seats.classList.toggle('is-lite', layout.chairs.length > LITE_DETAIL_SEATS);
        placeSurface(refs.screenStage, layout.screen.width, layout.screen.height,
                'translate3d(0, ' + (-layout.screen.centerElevation).toFixed(1) + 'px, 0px)');
        layout.chairs.forEach(renderChair);
        renderRowTags(layout);
        // A ring on the floor reads as an orange smear once chairs occlude it,
        // so the marker stands above the seat facing the overview camera.
        refs.halo.style.transform = 'translate3d(' + layout.eye.x.toFixed(1) + 'px, '
                + (-(layout.selectedDepth * GEO.riser + GEO.chairHeight + 96)).toFixed(1) + 'px, '
                + layout.eye.z.toFixed(1) + 'px)';
        renderMap(layout);
        currentCamera = null;
        presets = buildCameraPresets(layout, viewportSize());
        refs.subtitle.textContent = 'Ghế ' + options.seat.seatKey + ' · Hàng ' + options.seat.rowLabel
                + ' · ' + seatTypeLabel(options.seat.seatType);
    }

    function showTrailer(url, title) {
        var embed = trailerEmbedUrl(url);
        refs.filmTitle.textContent = title || options.filmTitle;
        if (embed) {
            refs.iframe.src = embed;
            refs.iframe.hidden = false;
            refs.placeholder.hidden = true;
        } else {
            refs.iframe.src = 'about:blank';
            refs.iframe.hidden = true;
            refs.placeholder.hidden = false;
            refs.placeholder.querySelector('small').textContent = 'Phim này chưa có trailer để phát.';
        }
    }

    function unwrapData(payload) {
        return payload && Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload;
    }

    function loadFilm(filmId) {
        if (!filmId) {
            showTrailer(options.trailerUrl, options.filmTitle);
            return Promise.resolve();
        }
        if (filmsCache.has(filmId) && filmsCache.get(filmId).trailerUrl != null) {
            var cached = filmsCache.get(filmId);
            showTrailer(cached.trailerUrl, cached.title);
            return Promise.resolve(cached);
        }
        var activeRequest = ++requestId;
        refs.placeholder.hidden = false;
        refs.placeholder.querySelector('small').textContent = 'Đang tải trailer…';
        return fetch(options.contextPath + '/api/v1/films/' + filmId, {headers: {'Accept': 'application/json'}})
                .then(function (response) {
                    if (!response.ok) throw new Error('HTTP ' + response.status);
                    return response.json();
                }).then(function (payload) {
                    if (activeRequest !== requestId) return null;
                    var data = unwrapData(payload) || {};
                    var film = data.film || data;
                    filmsCache.set(Number(film.id || filmId), film);
                    showTrailer(film.trailerUrl, film.title || options.filmTitle);
                    return film;
                }).catch(function () {
                    if (activeRequest === requestId) showTrailer(options.trailerUrl, options.filmTitle);
                    return null;
                });
    }

    function loadCatalog() {
        refs.filmSelect.innerHTML = '';
        var initial = createElement('option', '', 'Đang tải phim…');
        initial.value = '';
        refs.filmSelect.appendChild(initial);
        refs.filmSelect.disabled = true;
        if (!options.allowFilmPicker && options.filmId) {
            refs.filmSelect.closest('.cb-seat3d__film-field').hidden = true;
            return loadFilm(options.filmId);
        }
        refs.filmSelect.closest('.cb-seat3d__film-field').hidden = false;
        return fetch(options.contextPath + '/api/v1/films?status=showing&size=100', {headers: {'Accept': 'application/json'}})
                .then(function (response) {
                    if (!response.ok) throw new Error('HTTP ' + response.status);
                    return response.json();
                }).then(function (payload) {
                    var films = unwrapData(payload) || [];
                    refs.filmSelect.innerHTML = '';
                    films.forEach(function (film) {
                        filmsCache.set(Number(film.id), film);
                        var item = createElement('option', '', film.title || ('Phim #' + film.id));
                        item.value = String(film.id);
                        refs.filmSelect.appendChild(item);
                    });
                    var selectedId = options.filmId || (films[0] && films[0].id) || 0;
                    refs.filmSelect.value = String(selectedId || '');
                    refs.filmSelect.disabled = films.length === 0;
                    return loadFilm(Number(selectedId));
                }).catch(function () {
                    refs.filmSelect.innerHTML = '';
                    refs.filmSelect.appendChild(createElement('option', '', options.filmTitle || 'Không tải được danh sách phim'));
                    refs.filmSelect.disabled = true;
                    return loadFilm(options.filmId);
                });
    }

    function open(input) {
        options = normalizeOptions(input || {});
        if (!options.seat.seatKey) return {ok: false, error: 'Hãy chọn một ghế trước khi xem 3D.'};
        ensureModal();
        returnFocus = options.sourceElement || document.activeElement;
        motion.overview = {yaw: 0, pitch: 0, dolly: 1};
        motion.seat = {yaw: 0, pitch: 0, dolly: 1};
        if (options.sourceElement && options.sourceElement.getBoundingClientRect) {
            var rect = options.sourceElement.getBoundingClientRect();
            modal.style.setProperty('--open-x', (rect.left + rect.width / 2) + 'px');
            modal.style.setProperty('--open-y', (rect.top + rect.height / 2) + 'px');
        }
        modal.classList.add('is-open');
        modal.setAttribute('aria-hidden', 'false');
        document.documentElement.classList.add('cb-seat3d-open');
        document.body.classList.add('cb-seat3d-open');
        renderScene();
        setView('overview');
        showTrailer(options.trailerUrl, options.filmTitle);
        root.requestAnimationFrame(function () {
            modal.classList.add('is-ready');
            refs.shell.focus({preventScroll: true});
        });
        loadCatalog();
        return {ok: true};
    }

    function close() {
        if (!modal || !modal.classList.contains('is-open')) return;
        requestId += 1;
        stopFlight();
        cancelViewDrag();
        modal.classList.remove('is-ready');
        modal.setAttribute('aria-hidden', 'true');
        refs.iframe.src = 'about:blank';
        document.documentElement.classList.remove('cb-seat3d-open');
        document.body.classList.remove('cb-seat3d-open');
        root.setTimeout(function () { modal.classList.remove('is-open'); }, 180);
        if (returnFocus && returnFocus.focus) returnFocus.focus({preventScroll: true});
    }

    function handleKeydown(event) {
        if (!modal || !modal.classList.contains('is-open')) return;
        if (event.key === 'Escape') {
            event.preventDefault();
            if (dragSession) cancelViewDrag();
            else close();
            return;
        }
        if (!interactiveTarget(event.target)) {
            if (event.key === 'ArrowLeft') { event.preventDefault(); nudgeCamera(-6, 0, 1); return; }
            if (event.key === 'ArrowRight') { event.preventDefault(); nudgeCamera(6, 0, 1); return; }
            if (event.key === 'ArrowUp') { event.preventDefault(); nudgeCamera(0, 5, 1); return; }
            if (event.key === 'ArrowDown') { event.preventDefault(); nudgeCamera(0, -5, 1); return; }
            if (event.key === '+' || event.key === '=') { event.preventDefault(); nudgeCamera(0, 0, 1.12); return; }
            if (event.key === '-' || event.key === '_') { event.preventDefault(); nudgeCamera(0, 0, 1 / 1.12); return; }
        }
        if (event.key !== 'Tab') return;
        var focusable = Array.from(modal.querySelectorAll('button:not([disabled]), select:not([disabled]), iframe:not([hidden])'));
        if (!focusable.length) return;
        var first = focusable[0];
        var last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    return Object.freeze({
        open: open,
        close: close,
        youtubeId: youtubeId,
        trailerEmbedUrl: trailerEmbedUrl,
        normalizeSeat: normalizeSeat,
        buildTheatreLayout: buildTheatreLayout,
        buildCameraPresets: buildCameraPresets,
        buildOverviewCamera: buildOverviewCamera,
        toCameraSpace: toCameraSpace,
        projectPoint: projectPoint,
        splitFrameSpan: splitFrameSpan,
        cameraTransform: cameraTransform,
        floorElevationAt: floorElevationAt,
        clamp: clamp,
        rowIndex: rowIndex
    });
}));
