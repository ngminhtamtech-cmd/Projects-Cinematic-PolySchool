(function () {
    'use strict';

    var dataRoot = document.getElementById('revenueChartData');
    var chart = document.getElementById('revenueChart');
    var revenueTotal = document.getElementById('dashboardRevenueTotal');
    var orderTotal = document.getElementById('dashboardOrderTotal');

    if (!dataRoot || !chart) {
        if (revenueTotal) revenueTotal.textContent = 'Không có dữ liệu';
        if (orderTotal) orderTotal.textContent = 'Không có dữ liệu';
        return;
    }

    var rows = Array.prototype.map.call(dataRoot.querySelectorAll('li'), function (item) {
        return {
            label: item.dataset.label || '',
            revenue: Number(item.dataset.revenue || 0),
            orders: Number(item.dataset.orders || 0)
        };
    }).reverse();

    var totalRevenue = rows.reduce(function (sum, row) { return sum + row.revenue; }, 0);
    var totalOrders = rows.reduce(function (sum, row) { return sum + row.orders; }, 0);
    var currency = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });
    revenueTotal.textContent = currency.format(totalRevenue);
    orderTotal.textContent = new Intl.NumberFormat('vi-VN').format(totalOrders);

    if (!rows.length) return;

    var svg = chart.querySelector('svg');
    var width = 720;
    var height = 230;
    var left = 54;
    var right = 18;
    var top = 18;
    var bottom = 36;
    var innerWidth = width - left - right;
    var innerHeight = height - top - bottom;
    var max = Math.max.apply(null, rows.map(function (row) { return row.revenue; }));
    if (max <= 0) max = 1;

    var ns = 'http://www.w3.org/2000/svg';
    function node(name, attributes, text) {
        var element = document.createElementNS(ns, name);
        Object.keys(attributes || {}).forEach(function (key) { element.setAttribute(key, attributes[key]); });
        if (text !== undefined) element.textContent = text;
        return element;
    }

    for (var gridIndex = 0; gridIndex <= 4; gridIndex += 1) {
        var gridY = top + (innerHeight * gridIndex / 4);
        svg.appendChild(node('line', { x1: left, y1: gridY, x2: width - right, y2: gridY, class: 'chart-grid-line' }));
        var gridValue = max * (1 - gridIndex / 4);
        svg.appendChild(node('text', { x: left - 10, y: gridY + 4, class: 'chart-axis-label', 'text-anchor': 'end' }, compactNumber(gridValue)));
    }

    var points = rows.map(function (row, index) {
        var x = rows.length === 1 ? left + innerWidth / 2 : left + innerWidth * index / (rows.length - 1);
        var y = top + innerHeight * (1 - row.revenue / max);
        return { x: x, y: y, row: row };
    });
    var pointString = points.map(function (point) { return point.x + ',' + point.y; }).join(' ');
    var areaString = left + ',' + (top + innerHeight) + ' ' + pointString + ' ' + (width - right) + ',' + (top + innerHeight);
    svg.appendChild(node('polygon', { points: areaString, class: 'chart-area' }));
    svg.appendChild(node('polyline', { points: pointString, class: 'chart-line' }));

    points.forEach(function (point) {
        svg.appendChild(node('circle', { cx: point.x, cy: point.y, r: 4, class: 'chart-point' }));
        svg.appendChild(node('text', { x: point.x, y: height - 10, class: 'chart-axis-label', 'text-anchor': 'middle' }, displayDate(point.row.label)));
        var title = node('title', {}, point.row.label + ': ' + currency.format(point.row.revenue) + ' · ' + point.row.orders + ' đơn');
        svg.lastChild.appendChild(title);
    });

    function displayDate(value) {
        var parts = value.split('-');
        return parts.length === 3 ? parts[2] + '/' + parts[1] : value;
    }

    function compactNumber(value) {
        if (value >= 1000000000) return (value / 1000000000).toFixed(value >= 10000000000 ? 0 : 1) + 'T';
        if (value >= 1000000) return (value / 1000000).toFixed(value >= 10000000 ? 0 : 1) + 'tr';
        if (value >= 1000) return (value / 1000).toFixed(value >= 10000 ? 0 : 1) + 'k';
        return Math.round(value).toString();
    }
})();
