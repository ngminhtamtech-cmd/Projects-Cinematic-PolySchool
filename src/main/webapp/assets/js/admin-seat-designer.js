(function () {
    'use strict';

    var root = document.getElementById('seatDesigner');
    var Core = window.CineBookSeatDesignerCore;
    if (!root || !Core) return;

    var refs = {
        grid: document.getElementById('seatGrid'),
        scroll: document.getElementById('seatCanvasScroll'),
        status: document.getElementById('seatDesignerStatus'),
        modeHint: document.getElementById('seatModeHint'),
        dirty: document.getElementById('seatDirtyStatus'),
        headTotal: document.getElementById('seatHeadTotal'),
        total: document.getElementById('statTotalSeats'),
        standard: document.getElementById('statStandardSeats'),
        vip: document.getElementById('statVipSeats'),
        couple: document.getElementById('statCoupleSeats'),
        locked: document.getElementById('statLockedSeats'),
        selectionEmpty: document.getElementById('seatSelectionEmpty'),
        selectionCard: document.getElementById('seatSelectionCard'),
        selectionKey: document.getElementById('seatSelectionKey'),
        selectionType: document.getElementById('seatSelectionType'),
        selectionPosition: document.getElementById('seatSelectionPosition'),
        selectionPrice: document.getElementById('seatSelectionPrice'),
        selectionLock: document.getElementById('seatSelectionLock'),
        inspector: document.getElementById('seatInspector'),
        inspectorBackdrop: document.getElementById('seatInspectorBackdrop'),
        dangerMenu: document.getElementById('seatDangerMenu'),
        contextMenu: document.getElementById('seatContextMenu'),
        previewModal: document.getElementById('seatPreviewModal'),
        previewDialog: document.querySelector('#seatPreviewModal [role="dialog"]'),
        previewSummary: document.getElementById('seatPreviewSummary'),
        previewServer: document.getElementById('seatServerPreview'),
        previewGrid: document.getElementById('seatPreviewGrid'),
        previewSave: document.getElementById('seatPreviewSaveButton'),
        view3d: document.getElementById('seatDesigner3dButton'),
        confirmModal: document.getElementById('seatConfirmModal'),
        confirmDialog: document.querySelector('#seatConfirmModal [role="dialog"]'),
        confirmTitle: document.getElementById('seatConfirmTitle'),
        confirmMessage: document.getElementById('seatConfirmMessage'),
        confirmButton: document.getElementById('seatConfirmButton'),
        form: document.getElementById('saveSeatsForm'),
        seatsInput: document.getElementById('seatsJsonInput')
    };

    var rawSeats = [];
    try {
        rawSeats = JSON.parse(document.getElementById('seatsData').textContent || '[]');
    } catch (error) {
        rawSeats = [];
    }

    var state = Core.createState(rawSeats);
    var appliedInitialTemplate = false;
    if (rawSeats.length === 0) {
        Core.applyDefaultTemplate(state);
        appliedInitialTemplate = true;
    }
    var numberFormatter = new Intl.NumberFormat('vi-VN');
    var pointerSession = null;
    var previewCells = [];
    var previewIsValid = false;
    var previewError = '';
    var previewFrame = 0;
    var contextSeatKey = null;
    var confirmCallback = null;
    var modalReturnFocus = null;
    var previewApproved = false;
    var previewRequestId = 0;
    var isSubmitting = false;

    function iconLock() {
        return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>';
    }

    function typeLabel(type) {
        if (type === 'vip') return 'VIP';
        if (type === 'couple') return 'Ghế đôi';
        return 'Ghế thường';
    }

    function announce(message, tone) {
        refs.status.textContent = message;
        refs.status.dataset.tone = tone || 'info';
        if (tone === 'error') refs.status.setAttribute('role', 'alert');
        else refs.status.removeAttribute('role');
    }

    function currentCell() {
        var active = document.activeElement;
        if (active && active.closest && active.closest('#seatGrid') && active.dataset.row && active.dataset.col) {
            return {rowLabel: active.dataset.row, seatNumber: Number(active.dataset.col)};
        }
        var selected = Core.seatByKey(state, state.activeSeatKey);
        return selected ? {rowLabel: selected.rowLabel, seatNumber: selected.seatNumber} : null;
    }

    function maxUsedColumn() {
        return state.seats.reduce(function (max, seat) { return Math.max(max, seat.seatNumber); }, 0);
    }

    function minUsedColumn() {
        return state.seats.reduce(function (min, seat) { return Math.min(min, seat.seatNumber); }, Core.MAX_COLUMNS + 1);
    }

    function makeFace(content) {
        var face = document.createElement('span');
        face.className = 'seat-designer__seat-face';
        if (typeof content === 'string') face.innerHTML = content;
        return face;
    }

    function seatButton(seat, rowNumber, isActive) {
        var pair = seat.seatType === 'couple' ? Core.couplePair(state, seat) : [];
        if (seat.seatType === 'couple' && pair.length === 2 && seat.seatNumber !== pair[0].seatNumber) {
            return null;
        }
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'seat-designer__cell seat-designer__cell--' + seat.seatType;
        if (pair.length === 2) {
            button.classList.add('seat-designer__cell--couple');
            button.style.gridColumn = 'span 2';
            button.setAttribute('aria-colspan', '2');
        }
        if (seat.occupied || pair.some(function (item) { return item.occupied; })) {
            button.classList.add('seat-designer__cell--locked');
            button.setAttribute('aria-disabled', 'true');
        }
        if (isActive) button.classList.add('is-active');
        if (state.moveSourceKey && (state.moveSourceKey === seat.seatKey
                || pair.some(function (item) { return item.seatKey === state.moveSourceKey; }))) {
            button.classList.add('is-drag-source');
        }
        button.dataset.row = seat.rowLabel;
        button.dataset.col = String(seat.seatNumber);
        button.dataset.seatKey = seat.seatKey;
        button.setAttribute('role', 'gridcell');
        button.setAttribute('aria-rowindex', String(rowNumber));
        button.setAttribute('aria-colindex', String(seat.seatNumber));
        var visualLabel = pair.length === 2 ? pair[0].seatNumber + '–' + pair[1].seatNumber : String(seat.seatNumber);
        var locked = button.classList.contains('seat-designer__cell--locked');
        var aria = 'Hàng ' + seat.rowLabel + ', ghế ' + visualLabel + ', ' + typeLabel(seat.seatType)
                + ', phụ thu ' + numberFormatter.format(seat.priceSurcharge) + ' đồng'
                + (locked ? ', đang khóa' : ', có thể chỉnh sửa');
        button.setAttribute('aria-label', aria);
        button.title = aria;
        button.tabIndex = -1;
        button.appendChild(makeFace(visualLabel + (locked ? iconLock() : '')));
        return button;
    }

    function slotButton(rowLabel, column, rowNumber, isGrowth, isActive) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'seat-designer__cell seat-designer__cell--slot';
        if (isGrowth) button.classList.add('seat-designer__cell--growth');
        if (isActive) button.classList.add('is-active');
        if (state.rangeStart && state.rangeStart.rowLabel === rowLabel && state.rangeStart.seatNumber === column) {
            button.classList.add('is-range-anchor');
        }
        button.dataset.row = rowLabel;
        button.dataset.col = String(column);
        button.setAttribute('role', 'gridcell');
        button.setAttribute('aria-rowindex', String(rowNumber));
        button.setAttribute('aria-colindex', String(column));
        button.setAttribute('aria-label', 'Ô trống hàng ' + rowLabel + ', cột ' + column
                + (isGrowth ? ', vùng thêm ghế' : ''));
        button.title = button.getAttribute('aria-label');
        button.tabIndex = -1;
        button.appendChild(makeFace(''));
        return button;
    }

    function renderGrid() {
        var focusCell = currentCell();
        var growth = state.tool === 'add' || !!state.moveSourceKey;
        var layout = Core.visibleLayout(state, growth);
        var rowsDescending = layout.rows.slice().reverse();
        var usedMaxColumn = maxUsedColumn();
        var fragment = document.createDocumentFragment();
        refs.grid.innerHTML = '';
        refs.grid.style.gridTemplateColumns = '28px repeat(' + layout.columns + ', var(--seat-track)) 28px';
        refs.grid.setAttribute('aria-rowcount', String(rowsDescending.length));
        refs.grid.setAttribute('aria-colcount', String(layout.columns));

        var cornerLeft = document.createElement('span');
        cornerLeft.className = 'seat-designer__column-label';
        cornerLeft.setAttribute('aria-hidden', 'true');
        fragment.appendChild(cornerLeft);
        for (var headerColumn = layout.startColumn; headerColumn <= layout.endColumn; headerColumn += 1) {
            var columnLabel = document.createElement('span');
            columnLabel.className = 'seat-designer__column-label';
            columnLabel.textContent = headerColumn;
            columnLabel.setAttribute('aria-hidden', 'true');
            fragment.appendChild(columnLabel);
        }
        var cornerRight = cornerLeft.cloneNode(false);
        fragment.appendChild(cornerRight);

        var activeSet = false;
        var usedMinColumn = minUsedColumn();
        var usedMinRow = state.seats.reduce(function (min, seat) {
            return Math.min(min, Core.rowIndex(seat.rowLabel));
        }, Core.ROW_LABELS.length);
        var usedMaxRow = state.seats.reduce(function (max, seat) {
            return Math.max(max, Core.rowIndex(seat.rowLabel));
        }, -1);
        rowsDescending.forEach(function (rowLabel, rowPosition) {
            var labelLeft = document.createElement('span');
            labelLeft.className = 'seat-designer__row-label';
            labelLeft.textContent = rowLabel;
            labelLeft.setAttribute('aria-hidden', 'true');
            fragment.appendChild(labelLeft);

            var rowSeats = state.seats.filter(function (seat) { return seat.rowLabel === rowLabel; });
            for (var column = layout.startColumn; column <= layout.endColumn; column += 1) {
                var seat = rowSeats.find(function (item) { return item.seatNumber === column; });
                if (seat && seat.seatType === 'couple') {
                    var pair = Core.couplePair(state, seat);
                    if (pair.length === 2 && seat.seatNumber !== pair[0].seatNumber) continue;
                }
                var isActive = focusCell && focusCell.rowLabel === rowLabel && focusCell.seatNumber === column;
                var rowPositionIndex = Core.rowIndex(rowLabel);
                var button = seat ? seatButton(seat, rowPosition + 1, isActive)
                        : slotButton(rowLabel, column, rowPosition + 1,
                                growth && (rowPositionIndex < usedMinRow || rowPositionIndex > usedMaxRow
                                    || column < usedMinColumn || column > usedMaxColumn), isActive);
                if (!button) continue;
                if (isActive) activeSet = true;
                fragment.appendChild(button);
                if (seat && seat.seatType === 'couple' && Core.couplePair(state, seat).length === 2) column += 1;
            }

            var labelRight = labelLeft.cloneNode(true);
            fragment.appendChild(labelRight);
        });
        refs.grid.appendChild(fragment);

        var focusTarget = null;
        if (focusCell) {
            focusTarget = refs.grid.querySelector('[data-row="' + focusCell.rowLabel + '"][data-col="' + focusCell.seatNumber + '"]');
        }
        if (!focusTarget && !activeSet) focusTarget = refs.grid.querySelector('.seat-designer__cell');
        if (focusTarget) focusTarget.tabIndex = 0;
        applyPreviewClasses();
    }

    function renderStats() {
        var values = Core.stats(state);
        refs.headTotal.textContent = values.total;
        refs.total.textContent = values.total;
        refs.standard.textContent = values.standard;
        refs.vip.textContent = values.vip;
        refs.couple.textContent = values.couplePairs + ' cặp';
        refs.locked.textContent = values.locked;
    }

    function renderPrices() {
        root.querySelectorAll('[data-price-type]').forEach(function (input) {
            if (document.activeElement !== input) input.value = String(state.defaults[input.dataset.priceType]);
        });
    }

    function renderSelection() {
        var seat = Core.seatByKey(state, state.activeSeatKey);
        if (!seat) {
            refs.selectionEmpty.hidden = false;
            refs.selectionCard.hidden = true;
            if (refs.view3d) refs.view3d.disabled = true;
            return;
        }
        var pair = Core.couplePair(state, seat);
        var locked = seat.occupied || pair.some(function (item) { return item.occupied; });
        refs.selectionEmpty.hidden = true;
        refs.selectionCard.hidden = false;
        refs.selectionKey.textContent = pair.length === 2 ? pair[0].seatKey + '–' + pair[1].seatNumber : seat.seatKey;
        refs.selectionType.textContent = typeLabel(seat.seatType);
        refs.selectionPosition.textContent = 'Hàng ' + seat.rowLabel + ' · Ghế '
                + (pair.length === 2 ? pair[0].seatNumber + '–' + pair[1].seatNumber : seat.seatNumber);
        refs.selectionPrice.textContent = 'Phụ thu ' + numberFormatter.format(seat.priceSurcharge) + 'đ / ghế vật lý';
        refs.selectionLock.textContent = locked ? 'Đang có vé/giữ chỗ · Không thể sửa' : 'Có thể chỉnh sửa';
        refs.selectionCard.querySelectorAll('button').forEach(function (button) { button.disabled = locked; });
        if (refs.view3d) refs.view3d.disabled = false;
    }

    function openSeat3d() {
        var seat = Core.seatByKey(state, state.activeSeatKey);
        if (!seat) {
            announce('Hãy chọn một ghế trước khi xem góc nhìn 3D.', 'error');
            return;
        }
        if (!window.CineBookSeat3D) {
            announce('Không thể mở trình xem ghế 3D.', 'error');
            return;
        }
        var pair = Core.couplePair(state, seat);
        var focusSeat = pair.length === 2 ? pair[0] : seat;
        var source = refs.grid.querySelector('[data-seat-key="' + focusSeat.seatKey + '"]');
        var result = window.CineBookSeat3D.open({
            contextPath: root.dataset.contextPath,
            seat: focusSeat,
            seats: state.seats,
            sourceElement: source || refs.view3d,
            filmTitle: 'Màn hình ' + root.dataset.roomName,
            allowFilmPicker: true
        });
        if (!result || !result.ok) announce(result && result.error ? result.error : 'Không thể mở trình xem ghế 3D.', 'error');
    }

    function renderToolbar() {
        root.querySelectorAll('[data-tool]').forEach(function (button) {
            var active = button.dataset.tool === state.tool;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', active ? 'true' : 'false');
        });
        var undo = root.querySelector('[data-action="undo"]');
        var redo = root.querySelector('[data-action="redo"]');
        if (undo) undo.disabled = state.history.length === 0;
        if (redo) redo.disabled = state.future.length === 0;
        refs.modeHint.textContent = state.moveSourceKey ? 'Chọn ô đích để di chuyển'
                : (state.tool === 'add' ? 'Đang thêm ghế thường' : 'Đang chọn / di chuyển');
    }

    function renderDirtyState() {
        var dirty = Core.isDirty(state);
        refs.dirty.dataset.dirty = dirty ? 'true' : 'false';
        refs.dirty.querySelector('span:last-child').textContent = dirty ? 'Có thay đổi' : 'Đã lưu';
        previewApproved = false;
    }

    function render() {
        renderGrid();
        renderStats();
        renderPrices();
        renderSelection();
        renderToolbar();
        renderDirtyState();
    }

    function afterMutation(result, successMessage) {
        clearPointerPreview();
        if (!result || !result.ok) {
            announce(result && result.error ? result.error : 'Không thể thực hiện thao tác.', 'error');
            renderSelection();
            return false;
        }
        render();
        announce(successMessage, 'success');
        return true;
    }

    function cellFromElement(element) {
        var button = element && element.closest ? element.closest('.seat-designer__cell') : null;
        if (!button || !refs.grid.contains(button)) return null;
        return {
            element: button,
            rowLabel: button.dataset.row,
            seatNumber: Number(button.dataset.col),
            seatKey: button.dataset.seatKey || null,
            locked: button.getAttribute('aria-disabled') === 'true'
        };
    }

    function cellsForMovePreview(sourceKey, targetCell) {
        var validation = Core.validateMove(state, sourceKey, targetCell);
        if (validation.ok) return {cells: validation.cells, valid: true, error: ''};
        var source = Core.seatByKey(state, sourceKey);
        var cells = [{rowLabel: targetCell.rowLabel, seatNumber: targetCell.seatNumber}];
        if (source && source.seatType === 'couple') {
            cells.push({rowLabel: targetCell.rowLabel, seatNumber: targetCell.seatNumber + 1});
        }
        return {cells: cells, valid: false, error: validation.error};
    }

    function schedulePreview(cells, valid, error) {
        previewCells = cells || [];
        previewIsValid = !!valid;
        previewError = error || '';
        if (previewFrame) return;
        previewFrame = window.requestAnimationFrame(function () {
            previewFrame = 0;
            applyPreviewClasses();
        });
    }

    function applyPreviewClasses() {
        refs.grid.querySelectorAll('.is-preview-valid, .is-preview-invalid').forEach(function (element) {
            element.classList.remove('is-preview-valid', 'is-preview-invalid');
        });
        previewCells.forEach(function (cell) {
            var target = refs.grid.querySelector('[data-row="' + cell.rowLabel + '"][data-col="' + cell.seatNumber + '"]');
            if (target) target.classList.add(previewIsValid ? 'is-preview-valid' : 'is-preview-invalid');
        });
    }

    function clearPointerPreview() {
        previewCells = [];
        previewIsValid = false;
        previewError = '';
        if (previewFrame) {
            window.cancelAnimationFrame(previewFrame);
            previewFrame = 0;
        }
        applyPreviewClasses();
    }

    function autoScroll(clientX, clientY) {
        var rect = refs.scroll.getBoundingClientRect();
        var edge = 34;
        var speed = 14;
        var dx = clientX < rect.left + edge ? -speed : (clientX > rect.right - edge ? speed : 0);
        var dy = clientY < rect.top + edge ? -speed : (clientY > rect.bottom - edge ? speed : 0);
        if (dx || dy) refs.scroll.scrollBy(dx, dy);
    }

    function cancelPointerSession(message) {
        pointerSession = null;
        clearPointerPreview();
        if (message) announce(message, 'info');
    }

    function handleRangeEndpoint(cell) {
        if (!state.rangeStart) {
            state.rangeStart = {rowLabel: cell.rowLabel, seatNumber: cell.seatNumber};
            renderGrid();
            announce('Đã chọn điểm đầu ' + cell.rowLabel + cell.seatNumber + '. Chọn điểm cuối trên cùng hàng hoặc cùng cột.', 'info');
            return;
        }
        var range = Core.buildLinearRange(state.rangeStart, cell);
        if (!range.ok) {
            announce(range.error + ' Điểm đầu vẫn được giữ để bạn chọn lại.', 'error');
            return;
        }
        var validation = Core.validateRange(state, range.cells);
        if (!validation.ok) {
            announce(validation.error + ' Điểm đầu vẫn được giữ để bạn chọn lại.', 'error');
            schedulePreview(range.cells, false, validation.error);
            return;
        }
        var result = Core.addRange(state, range.cells);
        afterMutation(result, 'Đã thêm ' + range.cells.length + ' ghế thường theo trục ' + (range.axis === 'vertical' ? 'dọc.' : 'ngang.'));
    }

    function beginPointer(event) {
        if (event.pointerType === 'mouse' && event.button !== 0) return;
        var cell = cellFromElement(event.target);
        if (!cell) return;
        state.activeSeatKey = cell.seatKey;
        renderSelection();
        if (cell.locked) {
            announce('Ghế này đang có vé hoặc đang được giữ nên không thể chỉnh sửa.', 'error');
            return;
        }
        if (!cell.seatKey && state.moveSourceKey) {
            pointerSession = {kind: 'place-move', pointerId: event.pointerId, start: cell,
                startX: event.clientX, startY: event.clientY, dragged: false};
        } else if (!cell.seatKey && state.tool === 'add') {
            pointerSession = {kind: 'add', pointerId: event.pointerId, start: cell,
                startX: event.clientX, startY: event.clientY, dragged: false, axis: null, currentCells: []};
        } else if (cell.seatKey && state.tool === 'select') {
            pointerSession = {kind: 'move', pointerId: event.pointerId, start: cell,
                sourceKey: cell.seatKey, startX: event.clientX, startY: event.clientY,
                dragged: false, currentTarget: null};
        } else if (cell.seatKey) {
            pointerSession = {kind: 'cycle', pointerId: event.pointerId, start: cell,
                startX: event.clientX, startY: event.clientY, dragged: false};
        } else {
            return;
        }
        if (event.currentTarget.setPointerCapture) event.currentTarget.setPointerCapture(event.pointerId);
        event.preventDefault();
    }

    function movePointer(event) {
        if (!pointerSession || pointerSession.pointerId !== event.pointerId) return;
        var dx = event.clientX - pointerSession.startX;
        var dy = event.clientY - pointerSession.startY;
        var distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= 6 && !pointerSession.dragged) return;
        pointerSession.dragged = true;
        autoScroll(event.clientX, event.clientY);
        var hovered = cellFromElement(document.elementFromPoint(event.clientX, event.clientY));
        if (!hovered) return;
        if (pointerSession.kind === 'add') {
            if (!pointerSession.axis) pointerSession.axis = Math.abs(dx) >= Math.abs(dy) ? 'horizontal' : 'vertical';
            var endpoint = Core.projectEndpoint(pointerSession.start, hovered, pointerSession.axis);
            var range = Core.buildLinearRange(pointerSession.start, endpoint);
            var validation = range.ok ? Core.validateRange(state, range.cells) : range;
            pointerSession.currentCells = range.ok ? range.cells : [];
            previewError = validation.error || '';
            schedulePreview(pointerSession.currentCells, validation.ok, previewError);
        } else if (pointerSession.kind === 'move') {
            var movePreview = cellsForMovePreview(pointerSession.sourceKey, hovered);
            pointerSession.currentTarget = {rowLabel: hovered.rowLabel, seatNumber: hovered.seatNumber};
            previewError = movePreview.error;
            schedulePreview(movePreview.cells, movePreview.valid, movePreview.error);
        }
        event.preventDefault();
    }

    function endPointer(event) {
        if (!pointerSession || pointerSession.pointerId !== event.pointerId) return;
        var session = pointerSession;
        pointerSession = null;
        if (session.kind === 'add') {
            if (session.dragged) {
                if (!previewIsValid) {
                    announce(previewError || 'Phạm vi kéo không hợp lệ.', 'error');
                    clearPointerPreview();
                } else {
                    var result = Core.addRange(state, session.currentCells);
                    afterMutation(result, 'Đã thêm ' + session.currentCells.length + ' ghế thường theo trục '
                            + (session.axis === 'vertical' ? 'dọc.' : 'ngang.'));
                }
            } else {
                clearPointerPreview();
                handleRangeEndpoint(session.start);
            }
        } else if (session.kind === 'move') {
            if (session.dragged) {
                if (!previewIsValid || !session.currentTarget) {
                    announce(previewError || 'Vị trí thả ghế không hợp lệ.', 'error');
                    clearPointerPreview();
                } else {
                    var moveResult = Core.moveSeat(state, session.sourceKey, session.currentTarget);
                    afterMutation(moveResult, 'Đã di chuyển ghế và giữ nguyên toàn bộ phụ thu.');
                }
            } else {
                var cycleResult = Core.cycleSeatType(state, session.sourceKey);
                afterMutation(cycleResult, cycleResult.ok ? 'Đã đổi sang ' + typeLabel(cycleResult.type) + '.' : '');
            }
        } else if (session.kind === 'cycle' && !session.dragged) {
            var resultCycle = Core.cycleSeatType(state, session.start.seatKey);
            afterMutation(resultCycle, resultCycle.ok ? 'Đã đổi sang ' + typeLabel(resultCycle.type) + '.' : '');
        } else if (session.kind === 'place-move' && !session.dragged) {
            var placed = Core.moveSeat(state, state.moveSourceKey, session.start);
            afterMutation(placed, 'Đã di chuyển ghế và giữ nguyên toàn bộ phụ thu.');
        }
        event.preventDefault();
    }

    function cellToFocus(rowLabel, column) {
        var target = refs.grid.querySelector('[data-row="' + rowLabel + '"][data-col="' + column + '"]');
        if (target) return target;
        var physical = Core.seatAt(state, rowLabel, column);
        if (physical && physical.seatType === 'couple') {
            var pair = Core.couplePair(state, physical);
            if (pair.length === 2) return refs.grid.querySelector('[data-seat-key="' + pair[0].seatKey + '"]');
        }
        return null;
    }

    function moveGridFocus(cell, key) {
        var layout = Core.visibleLayout(state, state.tool === 'add' || !!state.moveSourceKey);
        var row = Core.rowIndex(cell.rowLabel);
        var column = cell.seatNumber;
        if (key === 'ArrowLeft') column -= 1;
        else if (key === 'ArrowRight') column += 1;
        else if (key === 'ArrowUp') row += 1;
        else if (key === 'ArrowDown') row -= 1;
        row = Math.max(0, Math.min(Core.ROW_LABELS.length - 1, row));
        column = Math.max(layout.startColumn, Math.min(layout.endColumn, column));
        var target = cellToFocus(Core.ROW_LABELS[row], column);
        if (target) {
            refs.grid.querySelectorAll('[tabindex="0"]').forEach(function (item) { item.tabIndex = -1; });
            target.tabIndex = 0;
            target.focus();
            var targetCell = cellFromElement(target);
            state.activeSeatKey = targetCell.seatKey;
            renderSelection();
        }
    }

    function activateCellByKeyboard(cell) {
        if (cell.locked) {
            announce('Ghế này đang bị khóa và không thể chỉnh sửa.', 'error');
            return;
        }
        if (!cell.seatKey && state.moveSourceKey) {
            afterMutation(Core.moveSeat(state, state.moveSourceKey, cell), 'Đã di chuyển ghế và giữ nguyên toàn bộ phụ thu.');
        } else if (!cell.seatKey && state.tool === 'add') {
            handleRangeEndpoint(cell);
        } else if (cell.seatKey) {
            afterMutation(Core.cycleSeatType(state, cell.seatKey), 'Đã đổi loại ghế.');
        }
    }

    function openContextMenu(event, cell) {
        if (!cell.seatKey) return;
        event.preventDefault();
        contextSeatKey = cell.seatKey;
        state.activeSeatKey = cell.seatKey;
        renderSelection();
        refs.contextMenu.hidden = false;
        refs.contextMenu.style.left = Math.min(event.clientX, window.innerWidth - 225) + 'px';
        refs.contextMenu.style.top = Math.min(event.clientY, window.innerHeight - 165) + 'px';
        refs.contextMenu.querySelector('button').focus();
    }

    function closeContextMenu() {
        refs.contextMenu.hidden = true;
    }

    function closeDangerMenu() {
        refs.dangerMenu.hidden = true;
        var trigger = root.querySelector('[data-action="toggle-danger"]');
        if (trigger) trigger.setAttribute('aria-expanded', 'false');
    }

    function setInspectorOpen(open) {
        refs.inspector.classList.toggle('is-open', open);
        refs.inspectorBackdrop.hidden = !open;
        root.querySelectorAll('[data-action="toggle-inspector"]').forEach(function (button) {
            button.setAttribute('aria-expanded', open ? 'true' : 'false');
        });
        if (open) {
            refs.inspector.scrollTop = 0;
            window.setTimeout(function () {
                var close = refs.inspector.querySelector('[data-action="close-inspector"]');
                if (close) close.focus({preventScroll: true});
            }, 0);
        }
    }

    function openModal(modal, dialog) {
        modalReturnFocus = document.activeElement;
        modal.classList.add('is-open');
        modal.setAttribute('aria-hidden', 'false');
        window.setTimeout(function () { dialog.focus(); }, 0);
    }

    function closeModal(modal) {
        modal.classList.remove('is-open');
        modal.setAttribute('aria-hidden', 'true');
        if (modal === refs.confirmModal) confirmCallback = null;
        if (modalReturnFocus && modalReturnFocus.focus) modalReturnFocus.focus();
        modalReturnFocus = null;
    }

    function openConfirm(title, message, label, callback) {
        refs.confirmTitle.textContent = title;
        refs.confirmMessage.textContent = message;
        refs.confirmButton.textContent = label || 'Xác nhận';
        confirmCallback = callback;
        openModal(refs.confirmModal, refs.confirmDialog);
    }

    function handleDeleteSeat(seatKey) {
        var seat = Core.seatByKey(state, seatKey);
        if (!seat) {
            announce('Hãy chọn một ghế trước khi xóa.', 'error');
            return;
        }
        openConfirm('Xóa ghế ' + seat.seatKey + '?',
                seat.seatType === 'couple' ? 'Cả cặp ghế đôi sẽ được xóa khỏi bản nháp.' : 'Ghế sẽ được xóa khỏi bản nháp và được liệt kê trong bước xem trước.',
                'Xóa ghế', function () {
                    afterMutation(Core.deleteSeat(state, seat.seatKey), 'Đã xóa ghế khỏi bản nháp.');
                });
    }

    function renderPreviewGrid() {
        refs.previewGrid.innerHTML = '';
        var rows = Array.from(new Set(state.seats.map(function (seat) { return seat.rowLabel; })))
                .sort(function (a, b) { return Core.rowIndex(b) - Core.rowIndex(a); });
        var maxColumn = maxUsedColumn();
        rows.forEach(function (rowLabel) {
            var row = document.createElement('div');
            row.className = 'seat-designer__preview-row';
            var left = document.createElement('strong');
            left.className = 'seat-designer__row-label';
            left.textContent = rowLabel;
            row.appendChild(left);
            for (var column = 1; column <= maxColumn; column += 1) {
                var seat = Core.seatAt(state, rowLabel, column);
                var cell = document.createElement('span');
                if (!seat) {
                    cell.className = 'seat-designer__preview-seat';
                    cell.style.visibility = 'hidden';
                    row.appendChild(cell);
                    continue;
                }
                var pair = seat.seatType === 'couple' ? Core.couplePair(state, seat) : [];
                if (pair.length === 2 && seat.seatNumber !== pair[0].seatNumber) continue;
                cell.className = 'seat-designer__preview-seat seat-designer__preview-seat--' + seat.seatType;
                if (seat.occupied || pair.some(function (item) { return item.occupied; })) cell.classList.add('seat-designer__preview-seat--locked');
                cell.textContent = pair.length === 2 ? pair[0].seatNumber + '–' + pair[1].seatNumber : seat.seatNumber;
                row.appendChild(cell);
                if (pair.length === 2) column += 1;
            }
            var right = left.cloneNode(true);
            row.appendChild(right);
            refs.previewGrid.appendChild(row);
        });
    }

    function renderPreviewSummary() {
        var diff = Core.diffState(state);
        var items = [
            ['Thêm', diff.added.length],
            ['Xóa/ngừng', diff.removed.length],
            ['Di chuyển', diff.moved.length],
            ['Đổi loại', diff.typeChanged.length],
            ['Đổi giá', diff.priceChanged.length]
        ];
        refs.previewSummary.innerHTML = '';
        items.forEach(function (item) {
            var card = document.createElement('div');
            card.className = 'seat-designer__preview-stat';
            var strong = document.createElement('strong');
            strong.textContent = item[1];
            var label = document.createElement('span');
            label.textContent = item[0];
            card.appendChild(strong);
            card.appendChild(label);
            refs.previewSummary.appendChild(card);
        });
    }

    function appendReloadButton() {
        var reload = document.createElement('button');
        reload.type = 'button';
        reload.className = 'seat-designer__button';
        reload.dataset.action = 'reload-room';
        reload.textContent = 'Tải lại dữ liệu phòng';
        reload.style.marginTop = '8px';
        refs.previewServer.appendChild(document.createElement('br'));
        refs.previewServer.appendChild(reload);
    }

    function openPreview() {
        var serialized = Core.serializeSeats(state);
        if (!serialized.ok) {
            announce(serialized.error, 'error');
            return;
        }
        previewApproved = false;
        refs.previewSave.disabled = true;
        renderPreviewSummary();
        renderPreviewGrid();
        refs.previewServer.dataset.tone = 'info';
        refs.previewServer.removeAttribute('role');
        refs.previewServer.textContent = 'Đang kiểm tra thay đổi với máy chủ…';
        openModal(refs.previewModal, refs.previewDialog);

        var requestId = ++previewRequestId;
        var csrf = refs.form.querySelector('input[name="_csrf"]');
        var roomId = refs.form.querySelector('input[name="roomId"]');
        var body = new URLSearchParams();
        body.set('_csrf', csrf ? csrf.value : '');
        body.set('roomId', roomId ? roomId.value : '');
        body.set('action', 'preview');
        body.set('seatsJson', JSON.stringify(serialized.seats));

        fetch(refs.form.getAttribute('action'), {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: body.toString()
        }).then(function (response) {
            return response.json().catch(function () { return {}; }).then(function (payload) {
                if (!response.ok) {
                    var requestError = new Error(payload.error || ('HTTP ' + response.status));
                    requestError.status = response.status;
                    throw requestError;
                }
                return payload;
            });
        }).then(function (serverPreview) {
            if (requestId !== previewRequestId) return;
            var referenced = (serverPreview.referencedSeatKeys || []);
            refs.previewServer.dataset.tone = 'info';
            refs.previewServer.textContent = 'Máy chủ xác nhận: hiện có ' + serverPreview.currentSeatCount
                    + ' ghế, sau khi lưu có ' + serverPreview.requestedSeatCount + ' ghế. Thêm '
                    + (serverPreview.addedSeatKeys || []).length + ', xóa/ngừng dùng '
                    + (serverPreview.removedSeatKeys || []).length + '. Ghế có lịch sử sẽ được ngừng dùng: '
                    + (referenced.length ? referenced.join(', ') : 'không có') + '.';
            previewApproved = true;
            refs.previewSave.disabled = false;
        }).catch(function (error) {
            if (requestId !== previewRequestId) return;
            refs.previewServer.dataset.tone = 'error';
            refs.previewServer.setAttribute('role', 'alert');
            refs.previewServer.textContent = error.status === 409
                    ? 'Sơ đồ trên máy chủ đã thay đổi trong lúc bạn chỉnh sửa. Bản nháp hiện tại vẫn được giữ; hãy tải lại dữ liệu phòng trước khi lưu.'
                    : 'Không thể xác nhận bản nháp: ' + error.message
                    + '. Dữ liệu trên trang vẫn được giữ nguyên và chưa ghi vào máy chủ.';
            appendReloadButton();
            previewApproved = false;
            refs.previewSave.disabled = true;
        });
    }

    function confirmSave() {
        if (!previewApproved) {
            announce('Cần preview thành công trước khi lưu.', 'error');
            return;
        }
        var serialized = Core.serializeSeats(state);
        if (!serialized.ok) {
            announce(serialized.error, 'error');
            return;
        }
        refs.seatsInput.value = JSON.stringify(serialized.seats);
        isSubmitting = true;
        refs.previewSave.disabled = true;
        refs.previewSave.textContent = 'Đang lưu…';
        refs.form.submit();
    }

    function handleDangerAction(action) {
        closeDangerMenu();
        var selected = Core.seatByKey(state, state.activeSeatKey);
        if (action === 'template') {
            openConfirm('Áp dụng mẫu ghế trung tâm?', 'Sơ đồ sẽ dùng khối ghế 8×10 và chừa hai hàng/cột trống bao quanh bốn phía. Ghế đang khóa sẽ chặn thao tác này.',
                    'Áp dụng mẫu', function () {
                        afterMutation(Core.applyDefaultTemplate(state), 'Đã đặt khối ghế mặc định vào giữa phòng.');
                    });
        } else if (action === 'clear') {
            openConfirm('Xóa toàn bộ sơ đồ?', 'Toàn bộ ghế có thể chỉnh sửa sẽ bị xóa khỏi bản nháp. Bạn vẫn có thể Undo trước khi lưu.',
                    'Xóa toàn bộ', function () {
                        afterMutation(Core.clearAll(state), 'Đã xóa toàn bộ ghế khỏi bản nháp.');
                    });
        } else if (!selected) {
            announce('Hãy chọn một ghế để xác định vị trí cần thao tác.', 'error');
        } else if (action === 'delete-seat') {
            handleDeleteSeat(selected.seatKey);
        } else if (action === 'delete-row') {
            openConfirm('Xóa hàng ' + selected.rowLabel + '?', 'Tất cả ghế trong hàng sẽ bị xóa khỏi bản nháp. Ghế khóa sẽ chặn thao tác.',
                    'Xóa hàng', function () {
                        afterMutation(Core.deleteRow(state, selected.rowLabel), 'Đã xóa hàng ' + selected.rowLabel + ' khỏi bản nháp.');
                    });
        } else if (action === 'delete-column') {
            openConfirm('Xóa cột ' + selected.seatNumber + '?', 'Ghế ở cột này trên mọi hàng sẽ bị xóa. Nửa còn lại của ghế đôi sẽ chuyển về ghế thường.',
                    'Xóa cột', function () {
                        afterMutation(Core.deleteColumn(state, selected.seatNumber), 'Đã xóa cột ' + selected.seatNumber + ' khỏi bản nháp.');
                    });
        }
    }

    function handleSeatAction(action, seatKey) {
        var seat = Core.seatByKey(state, seatKey);
        if (!seat) {
            announce('Không tìm thấy ghế đang chọn.', 'error');
            return;
        }
        if (action === 'cycle') {
            afterMutation(Core.cycleSeatType(state, seat.seatKey), 'Đã đổi loại ghế.');
        } else if (action === 'move') {
            if (seat.occupied) {
                announce('Ghế đang bị khóa nên không thể di chuyển.', 'error');
                return;
            }
            state.moveSourceKey = seat.seatKey;
            state.tool = 'select';
            state.rangeStart = null;
            render();
            announce('Đã chọn ghế ' + seat.seatKey + '. Bấm một ô trống để đặt ghế; giá hiện tại sẽ được giữ nguyên.', 'info');
        } else if (action === 'delete') {
            handleDeleteSeat(seat.seatKey);
        }
    }

    refs.grid.addEventListener('pointerdown', beginPointer);
    refs.grid.addEventListener('pointermove', movePointer);
    refs.grid.addEventListener('pointerup', endPointer);
    refs.grid.addEventListener('pointercancel', function () {
        cancelPointerSession('Đã hủy thao tác kéo; bản nháp không thay đổi.');
    });
    refs.grid.addEventListener('lostpointercapture', function () {
        if (pointerSession) cancelPointerSession('Đã hủy thao tác kéo; bản nháp không thay đổi.');
    });
    refs.grid.addEventListener('focusin', function (event) {
        var cell = cellFromElement(event.target);
        if (!cell) return;
        refs.grid.querySelectorAll('[tabindex="0"]').forEach(function (item) {
            if (item !== cell.element) item.tabIndex = -1;
        });
        cell.element.tabIndex = 0;
        state.activeSeatKey = cell.seatKey;
        renderSelection();
    });
    refs.grid.addEventListener('keydown', function (event) {
        var cell = cellFromElement(event.target);
        if (!cell) return;
        if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].indexOf(event.key) !== -1) {
            event.preventDefault();
            moveGridFocus(cell, event.key);
        } else if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            activateCellByKeyboard(cell);
        } else if (event.key === 'Delete' && cell.seatKey) {
            event.preventDefault();
            handleDeleteSeat(cell.seatKey);
        } else if (event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10')) {
            var rect = event.target.getBoundingClientRect();
            openContextMenu({preventDefault: function () {}, clientX: rect.left, clientY: rect.bottom}, cell);
        }
    });
    refs.grid.addEventListener('contextmenu', function (event) {
        var cell = cellFromElement(event.target);
        if (cell && cell.seatKey) openContextMenu(event, cell);
    });

    root.addEventListener('click', function (event) {
        var tool = event.target.closest('[data-tool]');
        if (tool) {
            state.tool = tool.dataset.tool;
            state.rangeStart = null;
            state.moveSourceKey = null;
            clearPointerPreview();
            render();
            announce(state.tool === 'add'
                    ? 'Kéo qua ô trống hoặc bấm điểm đầu–điểm cuối để thêm ghế thường.'
                    : 'Kéo một ghế sang ô trống. Bấm ghế không kéo để đổi loại.', 'info');
            return;
        }
        var action = event.target.closest('[data-action]');
        if (action) {
            var name = action.dataset.action;
            if (name === 'undo') afterMutation(Core.undo(state), 'Đã hoàn tác thao tác gần nhất.');
            else if (name === 'redo') afterMutation(Core.redo(state), 'Đã làm lại thao tác gần nhất.');
            else if (name === 'preview' || name === 'save') openPreview();
            else if (name === 'view-3d') openSeat3d();
            else if (name === 'confirm-save') confirmSave();
            else if (name === 'toggle-inspector') setInspectorOpen(!refs.inspector.classList.contains('is-open'));
            else if (name === 'close-inspector') setInspectorOpen(false);
            else if (name === 'toggle-danger') {
                refs.dangerMenu.hidden = !refs.dangerMenu.hidden;
                action.setAttribute('aria-expanded', refs.dangerMenu.hidden ? 'false' : 'true');
            }
            return;
        }
        var selectionAction = event.target.closest('[data-selection-action]');
        if (selectionAction) handleSeatAction(selectionAction.dataset.selectionAction, state.activeSeatKey);
        var dangerAction = event.target.closest('[data-danger-action]');
        if (dangerAction) handleDangerAction(dangerAction.dataset.dangerAction);
    });

    root.addEventListener('input', function (event) {
        var input = event.target.closest('[data-price-type]');
        if (!input) return;
        var result = Core.setDefaultPrice(state, input.dataset.priceType, input.value);
        if (!result.ok) announce(result.error, 'error');
        else announce('Đã cập nhật giá mặc định ' + typeLabel(input.dataset.priceType)
                + ' thành ' + numberFormatter.format(result.price) + 'đ. Giá ghế hiện có không thay đổi.', 'info');
    });

    refs.contextMenu.addEventListener('click', function (event) {
        var action = event.target.closest('[data-context-action]');
        if (!action) return;
        closeContextMenu();
        handleSeatAction(action.dataset.contextAction, contextSeatKey);
    });

    document.addEventListener('click', function (event) {
        if (!event.target.closest('#seatContextMenu') && !event.target.closest('#seatGrid')) closeContextMenu();
        if (!event.target.closest('.seat-designer__danger-wrap')) closeDangerMenu();
        var close = event.target.closest('[data-modal-close]');
        if (close) closeModal(close.dataset.modalClose === 'preview' ? refs.previewModal : refs.confirmModal);
        if (event.target === refs.previewModal) closeModal(refs.previewModal);
        if (event.target === refs.confirmModal) closeModal(refs.confirmModal);
        if (event.target.closest('[data-action="reload-room"]')) window.location.reload();
    });

    refs.confirmButton.addEventListener('click', function () {
        var callback = confirmCallback;
        closeModal(refs.confirmModal);
        if (callback) callback();
    });

    refs.previewSave.addEventListener('click', confirmSave);

    document.addEventListener('keydown', function (event) {
        var targetTag = event.target && event.target.tagName ? event.target.tagName.toLowerCase() : '';
        var editing = targetTag === 'input' || targetTag === 'textarea' || targetTag === 'select';
        if (!editing && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
            event.preventDefault();
            afterMutation(event.shiftKey ? Core.redo(state) : Core.undo(state), event.shiftKey ? 'Đã làm lại thao tác.' : 'Đã hoàn tác thao tác.');
            return;
        }
        if (!editing && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'y') {
            event.preventDefault();
            afterMutation(Core.redo(state), 'Đã làm lại thao tác.');
            return;
        }
        var openModalElement = refs.confirmModal.classList.contains('is-open') ? refs.confirmModal
                : (refs.previewModal.classList.contains('is-open') ? refs.previewModal : null);
        if (event.key === 'Escape') {
            if (openModalElement) closeModal(openModalElement);
            else if (refs.inspector.classList.contains('is-open')) setInspectorOpen(false);
            else if (!refs.contextMenu.hidden) closeContextMenu();
            else {
                state.rangeStart = null;
                state.moveSourceKey = null;
                cancelPointerSession();
                render();
                announce('Đã hủy thao tác hiện tại; bản nháp không thay đổi.', 'info');
            }
            return;
        }
        if (openModalElement && event.key === 'Tab') {
            var focusable = Array.from(openModalElement.querySelectorAll('button:not([disabled]), a[href], input:not([disabled])'));
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
    });

    window.addEventListener('beforeunload', function (event) {
        if (!isSubmitting && Core.isDirty(state)) {
            event.preventDefault();
            event.returnValue = '';
        }
    });

    render();
    announce(appliedInitialTemplate
            ? 'Đã đặt sẵn khối ghế trung tâm; hai hàng/cột trống bao quanh dùng để mở rộng.'
            : 'Kéo qua ô trống để thêm ghế; vùng nét đứt bao quanh sơ đồ là khu vực mở rộng.', 'info');
}());
