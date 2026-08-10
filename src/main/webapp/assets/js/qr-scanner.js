/*
 * qr-scanner.js — Quét mã QR vé xem phim bằng camera tại quầy.
 *
 * Hỗ trợ song song 2 cơ chế giải mã QR:
 *  1. Native BarcodeDetector API (hardware-accelerated nếu trình duyệt có sẵn).
 *  2. jsQR library fallback (giải mã trực tiếp qua HTML5 Canvas cho 100% trình duyệt
 *     Chrome / Edge / Firefox / Safari trên máy tính và điện thoại).
 */
(function () {
    'use strict';

    var SCAN_INTERVAL_MS = 200;
    // Chặn quét trùng: cùng một mã trong 3 giây chỉ xử lý 1 lần.
    var DUPLICATE_WINDOW_MS = 3000;

    var video = null;
    var statusBox = null;
    var startButton = null;
    var stopButton = null;
    var codeInput = null;
    var scanForm = null;

    var stream = null;
    var detector = null;
    var canvas = null;
    var canvasCtx = null;
    var timerId = null;
    var lastCode = null;
    var lastCodeAt = 0;

    function setStatus(message, tone) {
        if (!statusBox) {
            return;
        }
        statusBox.textContent = message;
        statusBox.className = 'scanner-status scanner-status--' + (tone || 'idle');
    }

    function supported() {
        return navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === 'function';
    }

    function stopScanner(message, tone) {
        if (timerId !== null) {
            clearInterval(timerId);
            timerId = null;
        }
        if (stream) {
            stream.getTracks().forEach(function (track) {
                track.stop();
            });
            stream = null;
        }
        if (video) {
            video.srcObject = null;
        }
        document.body.classList.remove('scanner-live');
        if (startButton) {
            startButton.hidden = false;
        }
        if (stopButton) {
            stopButton.hidden = true;
        }
        if (message) {
            setStatus(message, tone || 'idle');
        }
    }

    function onDetected(rawValue) {
        var code = (rawValue || '').trim();
        if (!code) {
            return;
        }

        var now = Date.now();
        if (code === lastCode && (now - lastCodeAt) < DUPLICATE_WINDOW_MS) {
            return;
        }
        lastCode = code;
        lastCodeAt = now;

        setStatus('Đã quét: ' + code + ' — đang tra cứu…', 'ok');
        stopScanner();

        if (codeInput) {
            codeInput.value = code;
        }
        if (scanForm) {
            ensureCsrfField(scanForm);
            scanForm.submit();
        }
    }

    // form.submit() bo qua trinh xu ly 'submit' cua trinh duyet, nen neu form duoc render thieu
    // token thi request se bi CsrfFilter tu choi 403 ma khong co gi bao truoc. Bo sung o day cho chac.
    // Nguon token chinh van la hidden input do JSP sinh ra - day chi la luoi do.
    function ensureCsrfField(form) {
        if (form.querySelector('input[name="_csrf"]')) {
            return;
        }
        var match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
        if (!match) {
            return;
        }
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = '_csrf';
        input.value = decodeURIComponent(match[1]);
        form.appendChild(input);
    }

    function tickCanvas() {
        if (typeof window.jsQR !== 'function') {
            return;
        }
        if (!canvas) {
            canvas = document.createElement('canvas');
            canvasCtx = canvas.getContext('2d', { willReadFrequently: true });
        }
        var width = video.videoWidth;
        var height = video.videoHeight;
        if (!width || !height) {
            return;
        }

        if (canvas.width !== width || canvas.height !== height) {
            canvas.width = width;
            canvas.height = height;
        }

        canvasCtx.drawImage(video, 0, 0, width, height);
        var imageData = canvasCtx.getImageData(0, 0, width, height);
        var qrCode = window.jsQR(imageData.data, imageData.width, imageData.height, {
            inversionAttempts: "dontInvert"
        });

        if (qrCode && qrCode.data) {
            onDetected(qrCode.data);
        }
    }

    function tick() {
        if (!video || video.readyState !== video.HAVE_ENOUGH_DATA) {
            return;
        }

        if (detector) {
            detector.detect(video).then(function (codes) {
                if (codes && codes.length > 0) {
                    onDetected(codes[0].rawValue);
                } else {
                    tickCanvas();
                }
            }).catch(function () {
                tickCanvas();
            });
        } else {
            tickCanvas();
        }
    }

    function startScanner() {
        if (!supported()) {
            setStatus('Trình duyệt này không hỗ trợ truy cập camera. Vui lòng nhập mã vé thủ công bên dưới.', 'error');
            return;
        }

        setStatus('Đang mở camera…', 'idle');

        navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } },
            audio: false
        }).then(function (mediaStream) {
            stream = mediaStream;
            video.srcObject = mediaStream;
            video.setAttribute('playsinline', 'true');
            return video.play();
        }).then(function () {
            if (typeof window.BarcodeDetector !== 'undefined') {
                try {
                    detector = new window.BarcodeDetector({ formats: ['qr_code'] });
                } catch (e) {
                    detector = null;
                }
            }
            timerId = setInterval(tick, SCAN_INTERVAL_MS);
            document.body.classList.add('scanner-live');
            if (startButton) {
                startButton.hidden = true;
            }
            if (stopButton) {
                stopButton.hidden = false;
            }
            setStatus('Đưa mã QR của khách vào khung hình camera.', 'live');
        }).catch(function (err) {
            stopScanner();
            var name = err && err.name ? err.name : '';
            if (name === 'NotAllowedError' || name === 'SecurityError') {
                setStatus('Bạn đã từ chối quyền camera. Vui lòng nhấp vào biểu tượng camera/ổ khóa trên thanh địa chỉ Chrome để "Cho phép" rồi bấm lại "Bật camera".', 'error');
            } else if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
                setStatus('Không tìm thấy thiết bị camera nào trên máy tính này. Vui lòng nhập mã thủ công.', 'error');
            } else {
                setStatus('Không mở được camera: ' + (err && err.message ? err.message : name) + '. Vui lòng nhập mã thủ công.', 'error');
            }
        });
    }

    function init() {
        video = document.getElementById('qrVideo');
        statusBox = document.getElementById('scannerStatus');
        startButton = document.getElementById('btnStartScan');
        stopButton = document.getElementById('btnStopScan');
        codeInput = document.getElementById('scannedTicketCode');
        scanForm = document.getElementById('scanForm');

        if (!video || !startButton) {
            return;
        }

        var jsQrReady = typeof window.jsQR === 'function';
        document.documentElement.setAttribute('data-jsqr-ready', jsQrReady ? 'true' : 'false');

        if (!jsQrReady && typeof window.BarcodeDetector === 'undefined') {
            startButton.disabled = true;
            setStatus('Bộ giải mã QR không tải được. Vui lòng tải lại trang hoặc nhập mã vé thủ công.', 'error');
        } else if (!supported()) {
            startButton.disabled = true;
            setStatus('Trình duyệt không hỗ trợ WebRTC Camera. Vui lòng nhập mã vé thủ công.', 'error');
        } else if (!window.isSecureContext) {
            startButton.disabled = true;
            setStatus('Trang đang chạy qua kết nối HTTP không bảo mật nên trình duyệt chặn camera. Cần truy cập qua http://localhost:8080 (hoặc HTTPS). Vui lòng nhập mã vé thủ công.', 'error');
        } else {
            setStatus('Bấm "Bật camera" để bắt đầu quét mã QR.', 'idle');
        }

        startButton.addEventListener('click', startScanner);
        if (stopButton) {
            stopButton.addEventListener('click', function () {
                stopScanner('Đã tắt camera.', 'idle');
            });
        }

        document.addEventListener('visibilitychange', function () {
            if (document.hidden) {
                stopScanner('Đã tạm tắt camera khi rời trang.', 'idle');
            }
        });
        window.addEventListener('beforeunload', function () {
            stopScanner();
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
}());
