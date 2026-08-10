(function (root, factory) {
    'use strict';
    var api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    }
    root.CineBookSeatDesignerCore = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    var ROW_LABELS = 'ABCDEFGHIJKL'.split('');
    var MAX_COLUMNS = 24;
    var HISTORY_LIMIT = 50;
    var DEFAULT_PRICES = Object.freeze({standard: 0, vip: 20000, couple: 100000});
    var VALID_TYPES = Object.freeze({standard: true, vip: true, couple: true});

    function asNumber(value, fallback) {
        var parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : fallback;
    }

    function rowIndex(label) {
        return ROW_LABELS.indexOf(String(label || '').trim().toUpperCase());
    }

    function positionKey(rowLabel, seatNumber) {
        return String(rowLabel || '').trim().toUpperCase() + String(Number(seatNumber));
    }

    function cloneSeat(seat) {
        return {
            originKey: seat.originKey || null,
            seatKey: seat.seatKey,
            rowLabel: seat.rowLabel,
            seatNumber: seat.seatNumber,
            seatType: seat.seatType,
            priceSurcharge: seat.priceSurcharge,
            occupied: seat.occupied === true
        };
    }

    function cloneSeats(seats) {
        return (seats || []).map(cloneSeat);
    }

    function normalizeSeat(raw) {
        var row = String(raw && raw.rowLabel || '').trim().toUpperCase();
        var number = Math.trunc(asNumber(raw && raw.seatNumber, 0));
        var type = String(raw && raw.seatType || 'standard').trim().toLowerCase();
        if (!VALID_TYPES[type]) type = 'standard';
        var key = String(raw && raw.seatKey || positionKey(row, number)).trim().toUpperCase();
        return {
            originKey: raw && Object.prototype.hasOwnProperty.call(raw, 'originKey')
                    ? (raw.originKey || null) : (key || null),
            seatKey: key,
            rowLabel: row,
            seatNumber: number,
            seatType: type,
            priceSurcharge: Math.max(0, asNumber(raw && raw.priceSurcharge, DEFAULT_PRICES[type])),
            occupied: !!(raw && raw.occupied)
        };
    }

    function sortSeats(seats) {
        return seats.slice().sort(function (a, b) {
            var rowDiff = rowIndex(a.rowLabel) - rowIndex(b.rowLabel);
            if (rowDiff !== 0) return rowDiff;
            return a.seatNumber - b.seatNumber;
        });
    }

    function deriveDefaultPrices(seats, fallback) {
        var resolvedFallback = Object.assign({}, DEFAULT_PRICES, fallback || {});
        var normalized = sortSeats((seats || []).map(normalizeSeat));
        var result = {};
        Object.keys(DEFAULT_PRICES).forEach(function (type) {
            var counts = new Map();
            var firstIndexes = new Map();
            normalized.forEach(function (seat, index) {
                if (seat.seatType !== type) return;
                var price = seat.priceSurcharge;
                counts.set(price, (counts.get(price) || 0) + 1);
                if (!firstIndexes.has(price)) firstIndexes.set(price, index);
            });
            var chosen = resolvedFallback[type];
            var chosenCount = -1;
            var chosenIndex = Number.MAX_SAFE_INTEGER;
            counts.forEach(function (count, price) {
                var firstIndex = firstIndexes.get(price);
                if (count > chosenCount || (count === chosenCount && firstIndex < chosenIndex)) {
                    chosen = price;
                    chosenCount = count;
                    chosenIndex = firstIndex;
                }
            });
            result[type] = Math.max(0, asNumber(chosen, DEFAULT_PRICES[type]));
        });
        return result;
    }

    function stableSeatPayload(seats) {
        return JSON.stringify(sortSeats(seats).map(function (seat) {
            return [seat.seatKey, seat.rowLabel, seat.seatNumber, seat.seatType, seat.priceSurcharge];
        }));
    }

    function createState(initialSeats, options) {
        var normalized = sortSeats((initialSeats || []).map(normalizeSeat));
        var defaults = deriveDefaultPrices(normalized, options && options.defaultPrices);
        return {
            seats: cloneSeats(normalized),
            initialSeats: cloneSeats(normalized),
            initialPayload: stableSeatPayload(normalized),
            defaults: defaults,
            history: [],
            future: [],
            tool: 'add',
            rangeStart: null,
            activeSeatKey: null,
            moveSourceKey: null,
            revision: 0
        };
    }

    function snapshot(state) {
        return {
            seats: cloneSeats(state.seats),
            defaults: Object.assign({}, state.defaults),
            activeSeatKey: state.activeSeatKey,
            moveSourceKey: state.moveSourceKey
        };
    }

    function restoreSnapshot(state, saved) {
        state.seats = cloneSeats(saved.seats);
        state.defaults = Object.assign({}, saved.defaults);
        state.activeSeatKey = saved.activeSeatKey || null;
        state.moveSourceKey = saved.moveSourceKey || null;
        state.rangeStart = null;
        state.revision += 1;
    }

    function commit(state, mutate) {
        var before = snapshot(state);
        var result = mutate();
        if (!result || result.ok === false) return result || {ok: false, error: 'Thao tác không hợp lệ.'};
        state.history.push(before);
        if (state.history.length > HISTORY_LIMIT) state.history.shift();
        state.future = [];
        state.rangeStart = null;
        state.revision += 1;
        return result;
    }

    function undo(state) {
        if (!state.history.length) return {ok: false, error: 'Không còn thao tác để hoàn tác.'};
        var previous = state.history.pop();
        state.future.push(snapshot(state));
        restoreSnapshot(state, previous);
        return {ok: true};
    }

    function redo(state) {
        if (!state.future.length) return {ok: false, error: 'Không còn thao tác để làm lại.'};
        var next = state.future.pop();
        state.history.push(snapshot(state));
        restoreSnapshot(state, next);
        return {ok: true};
    }

    function isDirty(state) {
        return stableSeatPayload(state.seats) !== state.initialPayload;
    }

    function markSaved(state) {
        state.seats.forEach(function (seat) { seat.originKey = seat.seatKey; });
        state.initialSeats = cloneSeats(state.seats);
        state.initialPayload = stableSeatPayload(state.seats);
        state.history = [];
        state.future = [];
        state.revision += 1;
    }

    function setDefaultPrice(state, type, value) {
        if (!VALID_TYPES[type]) return {ok: false, error: 'Loại ghế không hợp lệ.'};
        var price = asNumber(value, -1);
        if (price < 0) return {ok: false, error: 'Phụ thu phải là số không âm.'};
        state.defaults[type] = price;
        return {ok: true, price: price};
    }

    function seatAt(state, rowLabel, seatNumber, ignoredKeys) {
        var ignored = ignoredKeys || new Set();
        return state.seats.find(function (seat) {
            return !ignored.has(seat.seatKey)
                    && seat.rowLabel === rowLabel
                    && seat.seatNumber === seatNumber;
        }) || null;
    }

    function seatByKey(state, seatKey) {
        return state.seats.find(function (seat) { return seat.seatKey === seatKey; }) || null;
    }

    function existingRows(state) {
        return Array.from(new Set(state.seats.map(function (seat) { return seat.rowLabel; })))
                .sort(function (a, b) { return rowIndex(a) - rowIndex(b); });
    }

    function highestExistingRowIndex(state) {
        return state.seats.reduce(function (max, seat) {
            return Math.max(max, rowIndex(seat.rowLabel));
        }, -1);
    }

    function lowestExistingRowIndex(state) {
        return state.seats.reduce(function (min, seat) {
            var index = rowIndex(seat.rowLabel);
            return index < 0 ? min : Math.min(min, index);
        }, ROW_LABELS.length);
    }

    function rowCanReceive(state, rowLabel) {
        var index = rowIndex(rowLabel);
        if (index < 0) return false;
        var existedInitially = state.initialSeats.some(function (seat) { return seat.rowLabel === rowLabel; });
        var existsNow = state.seats.some(function (seat) { return seat.rowLabel === rowLabel; });
        if (!state.initialSeats.length) return true;
        var initialLowest = state.initialSeats.reduce(function (min, seat) {
            return Math.min(min, rowIndex(seat.rowLabel));
        }, ROW_LABELS.length);
        var initialHighest = state.initialSeats.reduce(function (max, seat) {
            return Math.max(max, rowIndex(seat.rowLabel));
        }, -1);
        return existedInitially || existsNow || index < initialLowest || index > initialHighest;
    }

    function visibleLayout(state, growth) {
        var extra = growth === false ? 0 : 2;
        var usedRows = existingRows(state);
        var rows = [];
        var minRow = lowestExistingRowIndex(state);
        var maxRow = highestExistingRowIndex(state);
        if (maxRow < 0) {
            minRow = extra > 0 ? 2 : 0;
            maxRow = extra > 0 ? 9 : 0;
        } else {
            minRow = Math.max(0, minRow - extra);
            maxRow = Math.min(ROW_LABELS.length - 1, maxRow + extra);
        }
        for (var row = minRow; row <= maxRow; row += 1) rows.push(ROW_LABELS[row]);
        if (!rows.length) rows = usedRows.length ? usedRows : ['A'];
        var minColumn = state.seats.reduce(function (min, seat) {
            return Math.min(min, seat.seatNumber);
        }, MAX_COLUMNS + 1);
        var maxColumn = state.seats.reduce(function (max, seat) {
            return Math.max(max, seat.seatNumber);
        }, 0);
        if (maxColumn < 1) {
            minColumn = extra > 0 ? 3 : 1;
            maxColumn = extra > 0 ? 12 : 1;
        }
        var startColumn = Math.max(1, minColumn - extra);
        var endColumn = Math.min(MAX_COLUMNS, maxColumn + extra);
        return {
            rows: rows,
            columns: endColumn - startColumn + 1,
            startColumn: startColumn,
            endColumn: endColumn,
            growthRows: extra,
            growthColumns: extra
        };
    }

    function buildLinearRange(start, end) {
        if (!start || !end) return {ok: false, error: 'Thiếu điểm đầu hoặc điểm cuối.'};
        var startRow = String(start.rowLabel || '').toUpperCase();
        var endRow = String(end.rowLabel || '').toUpperCase();
        var startColumn = Math.trunc(asNumber(start.seatNumber, 0));
        var endColumn = Math.trunc(asNumber(end.seatNumber, 0));
        if (rowIndex(startRow) < 0 || rowIndex(endRow) < 0
                || startColumn < 1 || endColumn < 1
                || startColumn > MAX_COLUMNS || endColumn > MAX_COLUMNS) {
            return {ok: false, error: 'Phạm vi nằm ngoài giới hạn sơ đồ.'};
        }
        var cells = [];
        var axis;
        if (startRow === endRow) {
            axis = startColumn === endColumn ? 'single' : 'horizontal';
            var lowColumn = Math.min(startColumn, endColumn);
            var highColumn = Math.max(startColumn, endColumn);
            for (var column = lowColumn; column <= highColumn; column += 1) {
                cells.push({rowLabel: startRow, seatNumber: column});
            }
        } else if (startColumn === endColumn) {
            axis = 'vertical';
            var lowRow = Math.min(rowIndex(startRow), rowIndex(endRow));
            var highRow = Math.max(rowIndex(startRow), rowIndex(endRow));
            for (var row = lowRow; row <= highRow; row += 1) {
                cells.push({rowLabel: ROW_LABELS[row], seatNumber: startColumn});
            }
        } else {
            return {ok: false, error: 'Điểm đầu và điểm cuối phải nằm cùng hàng hoặc cùng cột.'};
        }
        return {ok: true, axis: axis, cells: cells};
    }

    function projectEndpoint(start, hovered, axis) {
        if (axis === 'horizontal') {
            return {rowLabel: start.rowLabel, seatNumber: hovered.seatNumber};
        }
        if (axis === 'vertical') {
            return {rowLabel: hovered.rowLabel, seatNumber: start.seatNumber};
        }
        return {rowLabel: hovered.rowLabel, seatNumber: hovered.seatNumber};
    }

    function validateRange(state, cells, ignoredKeys) {
        if (!cells || !cells.length) return {ok: false, error: 'Phạm vi không có ô ghế.'};
        var seen = new Set();
        var ignored = ignoredKeys || new Set();
        for (var index = 0; index < cells.length; index += 1) {
            var cell = cells[index];
            var key = positionKey(cell.rowLabel, cell.seatNumber);
            if (seen.has(key)) return {ok: false, error: 'Phạm vi có vị trí trùng lặp.'};
            seen.add(key);
            if (rowIndex(cell.rowLabel) < 0 || cell.seatNumber < 1 || cell.seatNumber > MAX_COLUMNS) {
                return {ok: false, error: 'Phạm vi vượt giới hạn A–L hoặc 1–24.'};
            }
            if (!rowCanReceive(state, cell.rowLabel)) {
                return {ok: false, error: 'Hàng mới chỉ được thêm ở mép phát triển của sơ đồ.'};
            }
            if (seatAt(state, cell.rowLabel, cell.seatNumber, ignored)) {
                return {ok: false, error: 'Phạm vi đang chồng lên ghế có sẵn.'};
            }
        }
        return {ok: true};
    }

    function addRange(state, cells) {
        var validation = validateRange(state, cells);
        if (!validation.ok) return validation;
        return commit(state, function () {
            cells.forEach(function (cell) {
                state.seats.push({
                    originKey: null,
                    seatKey: positionKey(cell.rowLabel, cell.seatNumber),
                    rowLabel: cell.rowLabel,
                    seatNumber: cell.seatNumber,
                    seatType: 'standard',
                    priceSurcharge: state.defaults.standard,
                    occupied: false
                });
            });
            state.seats = sortSeats(state.seats);
            return {ok: true, count: cells.length};
        });
    }

    function couplePair(state, seat) {
        if (!seat || seat.seatType !== 'couple') return [];
        var partnerNumber = seat.seatNumber % 2 === 1 ? seat.seatNumber + 1 : seat.seatNumber - 1;
        var partner = seatAt(state, seat.rowLabel, partnerNumber);
        if (!partner || partner.seatType !== 'couple') return [seat];
        return [seat, partner].sort(function (a, b) { return a.seatNumber - b.seatNumber; });
    }

    function cycleSeatType(state, seatKey) {
        var seat = seatByKey(state, seatKey);
        if (!seat) return {ok: false, error: 'Không tìm thấy ghế.'};
        if (seat.occupied) return {ok: false, error: 'Ghế đang có vé hoặc đang được giữ nên không thể sửa.'};
        if (seat.seatType === 'standard') {
            return commit(state, function () {
                seat.seatType = 'vip';
                seat.priceSurcharge = state.defaults.vip;
                return {ok: true, type: 'vip', affected: [seat.seatKey]};
            });
        }
        if (seat.seatType === 'vip') {
            var partnerNumber = seat.seatNumber % 2 === 1 ? seat.seatNumber + 1 : seat.seatNumber - 1;
            var partner = seatAt(state, seat.rowLabel, partnerNumber);
            if (!partner || partner.occupied || partner.seatType === 'couple') {
                return {ok: false, error: 'Không đủ ghế liền kề theo cặp lẻ–chẵn để tạo ghế đôi.'};
            }
            return commit(state, function () {
                seat.seatType = 'couple';
                partner.seatType = 'couple';
                seat.priceSurcharge = state.defaults.couple;
                partner.priceSurcharge = state.defaults.couple;
                return {ok: true, type: 'couple', affected: [seat.seatKey, partner.seatKey]};
            });
        }
        var pair = couplePair(state, seat);
        if (pair.length !== 2 || pair.some(function (item) { return item.occupied; })) {
            return {ok: false, error: 'Cặp ghế đôi không đầy đủ hoặc đang bị khóa.'};
        }
        return commit(state, function () {
            pair.forEach(function (item) {
                item.seatType = 'standard';
                item.priceSurcharge = state.defaults.standard;
            });
            return {ok: true, type: 'standard', affected: pair.map(function (item) { return item.seatKey; })};
        });
    }

    function validateMove(state, sourceKey, target) {
        var source = seatByKey(state, sourceKey);
        if (!source) return {ok: false, error: 'Không tìm thấy ghế cần di chuyển.'};
        if (source.occupied) return {ok: false, error: 'Ghế đang có vé hoặc đang được giữ nên không thể di chuyển.'};
        var group = source.seatType === 'couple' ? couplePair(state, source) : [source];
        if (source.seatType === 'couple' && group.length !== 2) {
            return {ok: false, error: 'Cặp ghế đôi hiện tại không đầy đủ.'};
        }
        if (group.some(function (seat) { return seat.occupied; })) {
            return {ok: false, error: 'Cặp ghế có thành viên đang bị khóa.'};
        }
        var firstTargetColumn = Math.trunc(asNumber(target && target.seatNumber, 0));
        var targetRow = String(target && target.rowLabel || '').toUpperCase();
        var cells = [{rowLabel: targetRow, seatNumber: firstTargetColumn}];
        if (group.length === 2) cells.push({rowLabel: targetRow, seatNumber: firstTargetColumn + 1});
        var ignored = new Set(group.map(function (seat) { return seat.seatKey; }));
        var validation = validateRange(state, cells, ignored);
        if (!validation.ok) return validation;
        return {ok: true, source: source, group: group, cells: cells};
    }

    function moveSeat(state, sourceKey, target) {
        var validation = validateMove(state, sourceKey, target);
        if (!validation.ok) return validation;
        return commit(state, function () {
            validation.group.sort(function (a, b) { return a.seatNumber - b.seatNumber; })
                    .forEach(function (seat, index) {
                var cell = validation.cells[index];
                seat.rowLabel = cell.rowLabel;
                seat.seatNumber = cell.seatNumber;
                seat.seatKey = positionKey(cell.rowLabel, cell.seatNumber);
            });
            state.activeSeatKey = validation.group[0].seatKey;
            state.moveSourceKey = null;
            state.seats = sortSeats(state.seats);
            return {ok: true, affected: validation.group.map(function (seat) { return seat.seatKey; })};
        });
    }

    function deleteSeat(state, seatKey) {
        var seat = seatByKey(state, seatKey);
        if (!seat) return {ok: false, error: 'Không tìm thấy ghế.'};
        var group = seat.seatType === 'couple' ? couplePair(state, seat) : [seat];
        if (group.some(function (item) { return item.occupied; })) {
            return {ok: false, error: 'Ghế đang có vé hoặc đang được giữ nên không thể xóa.'};
        }
        return commit(state, function () {
            var keys = new Set(group.map(function (item) { return item.seatKey; }));
            state.seats = state.seats.filter(function (item) { return !keys.has(item.seatKey); });
            state.activeSeatKey = null;
            return {ok: true, count: group.length};
        });
    }

    function deleteRow(state, rowLabel) {
        var rowSeats = state.seats.filter(function (seat) { return seat.rowLabel === rowLabel; });
        if (!rowSeats.length) return {ok: false, error: 'Hàng này chưa có ghế.'};
        if (rowSeats.some(function (seat) { return seat.occupied; })) {
            return {ok: false, error: 'Hàng có ghế đang bị khóa nên không thể xóa.'};
        }
        return commit(state, function () {
            state.seats = state.seats.filter(function (seat) { return seat.rowLabel !== rowLabel; });
            state.activeSeatKey = null;
            return {ok: true, count: rowSeats.length};
        });
    }

    function deleteColumn(state, column) {
        var targets = state.seats.filter(function (seat) { return seat.seatNumber === column; });
        if (!targets.length) return {ok: false, error: 'Cột này chưa có ghế.'};
        var affectedPairs = [];
        targets.forEach(function (seat) {
            if (seat.seatType === 'couple') affectedPairs = affectedPairs.concat(couplePair(state, seat));
        });
        if (targets.concat(affectedPairs).some(function (seat) { return seat.occupied; })) {
            return {ok: false, error: 'Cột có ghế đang bị khóa nên không thể xóa.'};
        }
        return commit(state, function () {
            var targetKeys = new Set(targets.map(function (seat) { return seat.seatKey; }));
            state.seats = state.seats.filter(function (seat) { return !targetKeys.has(seat.seatKey); });
            affectedPairs.forEach(function (seat) {
                if (!targetKeys.has(seat.seatKey)) {
                    seat.seatType = 'standard';
                    seat.priceSurcharge = state.defaults.standard;
                }
            });
            state.activeSeatKey = null;
            return {ok: true, count: targets.length};
        });
    }

    function clearAll(state) {
        if (state.seats.some(function (seat) { return seat.occupied; })) {
            return {ok: false, error: 'Sơ đồ có ghế đang bị khóa nên không thể xóa toàn bộ.'};
        }
        return commit(state, function () {
            var count = state.seats.length;
            state.seats = [];
            state.activeSeatKey = null;
            return {ok: true, count: count};
        });
    }

    function applyDefaultTemplate(state) {
        if (state.seats.some(function (seat) { return seat.occupied; })) {
            return {ok: false, error: 'Không thể áp dụng mẫu khi còn ghế đang bị khóa.'};
        }
        return commit(state, function () {
            var seats = [];
            // Khối 8×10 nằm giữa giới hạn A–L / 1–24, chừa đúng hai hàng và
            // hai cột ở bốn phía để người dùng mở rộng như một vành bao quanh tâm.
            ROW_LABELS.slice(2, 10).forEach(function (rowLabel, row) {
                for (var column = 3; column <= 12; column += 1) {
                    var type = row >= 5 ? 'standard' : (row >= 1 ? 'vip' : 'couple');
                    seats.push({
                        originKey: null,
                        seatKey: positionKey(rowLabel, column),
                        rowLabel: rowLabel,
                        seatNumber: column,
                        seatType: type,
                        priceSurcharge: state.defaults[type],
                        occupied: false
                    });
                }
            });
            state.seats = seats;
            state.activeSeatKey = null;
            return {ok: true, count: seats.length};
        });
    }

    function validateState(state) {
        if (!state.seats.length) return {ok: false, error: 'Sơ đồ phải có ít nhất một ghế.'};
        var positions = new Set();
        var keys = new Set();
        for (var index = 0; index < state.seats.length; index += 1) {
            var seat = state.seats[index];
            var position = positionKey(seat.rowLabel, seat.seatNumber);
            if (rowIndex(seat.rowLabel) < 0 || seat.seatNumber < 1 || seat.seatNumber > MAX_COLUMNS) {
                return {ok: false, error: 'Ghế ' + seat.seatKey + ' nằm ngoài giới hạn A–L hoặc 1–24.'};
            }
            if (positions.has(position)) return {ok: false, error: 'Hai ghế đang chiếm cùng vị trí ' + position + '.'};
            if (keys.has(seat.seatKey)) return {ok: false, error: 'Mã ghế ' + seat.seatKey + ' bị trùng.'};
            if (!Number.isFinite(seat.priceSurcharge) || seat.priceSurcharge < 0) {
                return {ok: false, error: 'Phụ thu của ghế ' + seat.seatKey + ' không hợp lệ.'};
            }
            positions.add(position);
            keys.add(seat.seatKey);
            if (seat.seatType === 'couple' && couplePair(state, seat).length !== 2) {
                return {ok: false, error: 'Ghế đôi ' + seat.seatKey + ' không có đủ cặp lẻ–chẵn.'};
            }
        }
        return {ok: true};
    }

    function serializeSeats(state) {
        var validation = validateState(state);
        if (!validation.ok) return {ok: false, error: validation.error, seats: []};
        return {
            ok: true,
            seats: sortSeats(state.seats).map(function (seat) {
                return {
                    rowLabel: seat.rowLabel,
                    seatNumber: seat.seatNumber,
                    seatType: seat.seatType,
                    seatKey: seat.seatKey,
                    priceSurcharge: seat.priceSurcharge
                };
            })
        };
    }

    function diffState(state) {
        var initialByKey = new Map();
        state.initialSeats.forEach(function (seat) { initialByKey.set(seat.seatKey, seat); });
        var representedOrigins = new Set();
        var added = [];
        var moved = [];
        var typeChanged = [];
        var priceChanged = [];
        state.seats.forEach(function (seat) {
            var initial = seat.originKey ? initialByKey.get(seat.originKey) : null;
            if (!initial) {
                added.push(seat.seatKey);
                return;
            }
            representedOrigins.add(seat.originKey);
            if (initial.rowLabel !== seat.rowLabel || initial.seatNumber !== seat.seatNumber
                    || initial.seatKey !== seat.seatKey) {
                moved.push({from: initial.seatKey, to: seat.seatKey});
            }
            if (initial.seatType !== seat.seatType) {
                typeChanged.push({seatKey: seat.seatKey, from: initial.seatType, to: seat.seatType});
            }
            if (initial.priceSurcharge !== seat.priceSurcharge) {
                priceChanged.push({seatKey: seat.seatKey, from: initial.priceSurcharge, to: seat.priceSurcharge});
            }
        });
        var removed = state.initialSeats.filter(function (seat) {
            return !representedOrigins.has(seat.seatKey);
        }).map(function (seat) { return seat.seatKey; });
        return {added: added, removed: removed, moved: moved, typeChanged: typeChanged, priceChanged: priceChanged};
    }

    function stats(state) {
        var result = {total: state.seats.length, standard: 0, vip: 0, coupleSeats: 0, couplePairs: 0, locked: 0};
        state.seats.forEach(function (seat) {
            if (seat.seatType === 'standard') result.standard += 1;
            else if (seat.seatType === 'vip') result.vip += 1;
            else if (seat.seatType === 'couple') result.coupleSeats += 1;
            if (seat.occupied) result.locked += 1;
        });
        result.couplePairs = Math.floor(result.coupleSeats / 2);
        return result;
    }

    return Object.freeze({
        ROW_LABELS: ROW_LABELS.slice(),
        MAX_COLUMNS: MAX_COLUMNS,
        DEFAULT_PRICES: DEFAULT_PRICES,
        createState: createState,
        normalizeSeat: normalizeSeat,
        deriveDefaultPrices: deriveDefaultPrices,
        positionKey: positionKey,
        rowIndex: rowIndex,
        seatAt: seatAt,
        seatByKey: seatByKey,
        couplePair: couplePair,
        visibleLayout: visibleLayout,
        buildLinearRange: buildLinearRange,
        projectEndpoint: projectEndpoint,
        validateRange: validateRange,
        addRange: addRange,
        cycleSeatType: cycleSeatType,
        validateMove: validateMove,
        moveSeat: moveSeat,
        deleteSeat: deleteSeat,
        deleteRow: deleteRow,
        deleteColumn: deleteColumn,
        clearAll: clearAll,
        applyDefaultTemplate: applyDefaultTemplate,
        validateState: validateState,
        serializeSeats: serializeSeats,
        diffState: diffState,
        stats: stats,
        setDefaultPrice: setDefaultPrice,
        undo: undo,
        redo: redo,
        isDirty: isDirty,
        markSaved: markSaved,
        sortSeats: sortSeats
    });
}));
