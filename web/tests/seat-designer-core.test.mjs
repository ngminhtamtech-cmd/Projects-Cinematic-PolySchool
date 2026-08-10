import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const Core = require('../../src/main/webapp/assets/js/admin-seat-designer-core.js');

function seat(rowLabel, seatNumber, seatType = 'standard', priceSurcharge = 0, occupied = false) {
    return {
        rowLabel,
        seatNumber,
        seatKey: `${rowLabel}${seatNumber}`,
        seatType,
        priceSurcharge,
        occupied
    };
}

test('add mode exposes exactly two growth rows and columns', () => {
    const state = Core.createState([seat('A', 1)]);
    assert.deepEqual(Core.visibleLayout(state, false), {
        rows: ['A'], columns: 1, startColumn: 1, endColumn: 1,
        growthRows: 0, growthColumns: 0
    });
    assert.deepEqual(Core.visibleLayout(state, true), {
        rows: ['A', 'B', 'C'], columns: 3, startColumn: 1, endColumn: 3,
        growthRows: 2, growthColumns: 2
    });

    assert.equal(Core.addRange(state, [{rowLabel: 'A', seatNumber: 3}]).ok, true);
    assert.equal(Core.visibleLayout(state, true).columns, 5);
});

test('center template leaves two usable rows and columns around all four sides', () => {
    const state = Core.createState([]);
    assert.equal(Core.applyDefaultTemplate(state).ok, true);
    const layout = Core.visibleLayout(state, true);
    assert.deepEqual(layout.rows, Core.ROW_LABELS);
    assert.equal(layout.startColumn, 1);
    assert.equal(layout.endColumn, 14);
    assert.equal(layout.columns, 14);
    assert.equal(Core.seatAt(state, 'C', 3).seatType, 'couple');
    assert.equal(Core.seatAt(state, 'J', 12).seatType, 'standard');
    assert.equal(Core.seatAt(state, 'A', 3), null);
    assert.equal(Core.seatAt(state, 'L', 12), null);
    assert.equal(state.seats.length, 80);
    assert.equal(Core.addRange(state, [{rowLabel: 'A', seatNumber: 3}]).ok, true);
    assert.equal(Core.addRange(state, [{rowLabel: 'B', seatNumber: 3}]).ok, true);
    assert.equal(Core.addRange(state, [{rowLabel: 'L', seatNumber: 3}]).ok, true);
    assert.equal(Core.addRange(state, [{rowLabel: 'K', seatNumber: 3}]).ok, true);
});

test('inclusive ranges support horizontal and vertical axes and reject diagonals', () => {
    assert.deepEqual(
            Core.buildLinearRange({rowLabel: 'A', seatNumber: 2}, {rowLabel: 'A', seatNumber: 4}),
            {
                ok: true,
                axis: 'horizontal',
                cells: [
                    {rowLabel: 'A', seatNumber: 2},
                    {rowLabel: 'A', seatNumber: 3},
                    {rowLabel: 'A', seatNumber: 4}
                ]
            }
    );
    assert.equal(
            Core.buildLinearRange({rowLabel: 'A', seatNumber: 2}, {rowLabel: 'C', seatNumber: 2}).cells.length,
            3
    );
    assert.equal(
            Core.buildLinearRange({rowLabel: 'A', seatNumber: 2}, {rowLabel: 'B', seatNumber: 3}).ok,
            false
    );
});

test('range transaction rejects collisions and limits without partial changes', () => {
    const state = Core.createState([seat('A', 2)]);
    const before = Core.serializeSeats(state).seats;
    const conflict = Core.addRange(state, [
        {rowLabel: 'A', seatNumber: 1},
        {rowLabel: 'A', seatNumber: 2},
        {rowLabel: 'A', seatNumber: 3}
    ]);
    assert.equal(conflict.ok, false);
    assert.deepEqual(Core.serializeSeats(state).seats, before);
    assert.equal(Core.addRange(state, [{rowLabel: 'L', seatNumber: 25}]).ok, false);
});

test('default prices use the mode and the first physical seat to break ties', () => {
    const state = Core.createState([
        seat('A', 1, 'vip', 25000),
        seat('A', 2, 'vip', 30000),
        seat('B', 1, 'vip', 30000),
        seat('B', 2, 'vip', 25000)
    ]);
    assert.equal(state.defaults.vip, 25000);
    assert.equal(state.defaults.standard, 0);
    assert.equal(state.defaults.couple, 100000);
});

test('click cycle is Standard to VIP to a complete Couple and back to Standard', () => {
    const state = Core.createState([seat('A', 1), seat('A', 2)]);
    Core.setDefaultPrice(state, 'standard', 5000);
    Core.setDefaultPrice(state, 'vip', 25000);
    Core.setDefaultPrice(state, 'couple', 120000);

    assert.equal(Core.cycleSeatType(state, 'A1').type, 'vip');
    assert.equal(Core.seatByKey(state, 'A1').priceSurcharge, 25000);

    assert.equal(Core.cycleSeatType(state, 'A1').type, 'couple');
    assert.deepEqual(
            [Core.seatByKey(state, 'A1'), Core.seatByKey(state, 'A2')]
                    .map(item => [item.seatType, item.priceSurcharge]),
            [['couple', 120000], ['couple', 120000]]
    );

    assert.equal(Core.cycleSeatType(state, 'A2').type, 'standard');
    assert.deepEqual(
            [Core.seatByKey(state, 'A1'), Core.seatByKey(state, 'A2')]
                    .map(item => [item.seatType, item.priceSurcharge]),
            [['standard', 5000], ['standard', 5000]]
    );
});

test('a Couple requires both physical odd-even members', () => {
    const incomplete = Core.createState([seat('A', 1, 'couple', 100000)]);
    assert.equal(Core.validateState(incomplete).ok, false);

    const noPartner = Core.createState([seat('A', 1, 'vip', 20000)]);
    assert.equal(Core.cycleSeatType(noPartner, 'A1').ok, false);
    assert.equal(Core.seatByKey(noPartner, 'A1').seatType, 'vip');
    assert.equal(Core.seatByKey(noPartner, 'A1').priceSurcharge, 20000);
});

test('moving preserves exact price and Undo/Redo restores position type and price', () => {
    const state = Core.createState([seat('A', 1, 'vip', 27500)]);
    assert.equal(Core.moveSeat(state, 'A1', {rowLabel: 'B', seatNumber: 2}).ok, true);
    assert.equal(Core.seatByKey(state, 'B2').priceSurcharge, 27500);

    assert.equal(Core.cycleSeatType(state, 'B2').ok, false, 'a VIP without a partner remains unchanged');
    assert.equal(Core.undo(state).ok, true);
    assert.deepEqual(
            [Core.seatByKey(state, 'A1').rowLabel, Core.seatByKey(state, 'A1').seatType,
                Core.seatByKey(state, 'A1').priceSurcharge],
            ['A', 'vip', 27500]
    );
    assert.equal(Core.redo(state).ok, true);
    assert.deepEqual(
            [Core.seatByKey(state, 'B2').rowLabel, Core.seatByKey(state, 'B2').seatType,
                Core.seatByKey(state, 'B2').priceSurcharge],
            ['B', 'vip', 27500]
    );
});

test('Undo restores both type and per-seat price and history is capped at 50 transactions', () => {
    const state = Core.createState([seat('A', 1, 'standard', 7000), seat('A', 2)]);
    Core.setDefaultPrice(state, 'vip', 33000);
    assert.equal(Core.cycleSeatType(state, 'A1').ok, true);
    assert.deepEqual([Core.seatByKey(state, 'A1').seatType, Core.seatByKey(state, 'A1').priceSurcharge], ['vip', 33000]);
    assert.equal(Core.undo(state).ok, true);
    assert.deepEqual([Core.seatByKey(state, 'A1').seatType, Core.seatByKey(state, 'A1').priceSurcharge], ['standard', 7000]);

    for (let index = 0; index < 55; index += 1) {
        assert.equal(Core.cycleSeatType(state, 'A1').ok, true);
    }
    assert.equal(state.history.length, 50);
});

test('serializer keeps each physical Couple record and its exact price', () => {
    const state = Core.createState([
        seat('A', 1, 'couple', 99000),
        seat('A', 2, 'couple', 101000)
    ]);
    const payload = Core.serializeSeats(state);
    assert.equal(payload.ok, true);
    assert.deepEqual(payload.seats, [
        {rowLabel: 'A', seatNumber: 1, seatType: 'couple', seatKey: 'A1', priceSurcharge: 99000},
        {rowLabel: 'A', seatNumber: 2, seatType: 'couple', seatKey: 'A2', priceSurcharge: 101000}
    ]);
});

test('locked seats reject moving, cycling and deletion', () => {
    const state = Core.createState([seat('A', 1, 'standard', 0, true)]);
    assert.equal(Core.moveSeat(state, 'A1', {rowLabel: 'A', seatNumber: 2}).ok, false);
    assert.equal(Core.cycleSeatType(state, 'A1').ok, false);
    assert.equal(Core.deleteSeat(state, 'A1').ok, false);
    assert.equal(state.history.length, 0);
});

test('diff reports added, removed, moved, type and price changes from origin keys', () => {
    const state = Core.createState([seat('A', 1), seat('A', 2)]);
    assert.equal(Core.moveSeat(state, 'A1', {rowLabel: 'B', seatNumber: 1}).ok, true);
    assert.equal(Core.cycleSeatType(state, 'A2').ok, true);
    const diff = Core.diffState(state);
    assert.deepEqual(diff.moved, [{from: 'A1', to: 'B1'}]);
    assert.deepEqual(diff.typeChanged, [{seatKey: 'A2', from: 'standard', to: 'vip'}]);
    assert.deepEqual(diff.priceChanged, [{seatKey: 'A2', from: 0, to: 20000}]);
});
