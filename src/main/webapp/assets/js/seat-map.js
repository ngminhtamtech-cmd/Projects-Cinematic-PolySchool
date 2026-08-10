(function () {
    const root = document.querySelector('[data-booking-root]');
    if (!root) return;

    const contextPath = root.dataset.contextPath;
    const initialShowtimeId = Number(root.dataset.showtimeId || 0);
    const basePrice = Number(root.dataset.basePrice || 0);
    const isLoggedIn = root.dataset.loggedIn === 'true';
    // Hạn giữ ghế do server quyết định (BookingService.HOLD_MINUTES), truyền xuống qua data attribute.
    // Chỉ dùng khi không gọi được API hạn giữ — không hard-code số phút trong file này.
    const holdFallbackSeconds = Math.max(60, Number(root.dataset.holdMinutes || 10) * 60);

    // CSRF: CsrfFilter phat cookie XSRF-TOKEN (khong HttpOnly) tren moi request GET.
    // Moi POST phai gui lai token qua header X-CSRF-Token, neu khong se bi tu choi 403.
    function csrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    const showtimesElement = document.getElementById('allShowtimesJson');
    const allShowtimes = (showtimesElement && showtimesElement.textContent) ? JSON.parse(showtimesElement.textContent) : (window.allShowtimes || []);

    // State variables
    let currentStep = 1;
    let selectedShowtimeId = initialShowtimeId;
    let selectedFilmId = 0;
    let selectedSeats = [];
    let currentVisualSeats = [];
    let selectedPreviewSeat = null;
    let comboSelections = {}; // comboId -> qty
    let selectedPaymentMethod = 'card';
    let currentOrderId = null;
    let countdownInterval = null;
    let seatPollInterval = null;
    let seatVersionEtag = null;
    let currentBasePrice = basePrice;
    let appliedPromotion = null;

    // Elements
    const stepper = document.querySelector('[data-stepper]');
    const btnContinue = document.getElementById('btnSideContinue');
    const btnBack = document.getElementById('btnSideBack');
    const sideErrorMessage = document.getElementById('sideErrorMessage');
    const sideTotalPrice = document.getElementById('sideTotalPrice');
    const sideDetailsList = document.getElementById('sideDetailsList');
    const sideCinemaInfo = document.getElementById('sideCinemaInfo');
    const sideFilmPoster = document.getElementById('sideFilmPoster');
    const sideFilmTitle = document.getElementById('sideFilmTitle');
    const sideFilmAge = document.getElementById('sideFilmAge');
    const seatView3dButton = document.getElementById('seatView3dButton');

    function updateSeat3dButton() {
        if (!seatView3dButton) return;
        seatView3dButton.disabled = !selectedPreviewSeat;
        seatView3dButton.setAttribute('aria-label', selectedPreviewSeat
            ? `Xem góc nhìn 3D từ ghế ${selectedPreviewSeat.seatKey}`
            : 'Chọn một ghế để xem góc nhìn 3D');
    }

    function selectedVisualSeat(visualSeats) {
        return visualSeats.find(seat => seat.physicalSeats.length > 0
            && seat.physicalSeats.every(physical => selectedSeats.some(selected => selected.id === physical.id))) || null;
    }

    if (seatView3dButton) {
        updateSeat3dButton();
        seatView3dButton.addEventListener('click', function () {
            if (!selectedPreviewSeat || !window.CineBookSeat3D) return;
            window.CineBookSeat3D.open({
                contextPath: contextPath,
                filmId: selectedFilmId,
                filmTitle: sideFilmTitle ? sideFilmTitle.textContent.trim() : 'Phim đã chọn',
                seat: selectedPreviewSeat,
                seats: currentVisualSeats,
                sourceElement: document.querySelector('.seat-cell.selected') || seatView3dButton,
                allowFilmPicker: false
            });
        });
    }

    // Parse parameters from URL for booking state restoration
    const urlParams = new URLSearchParams(window.location.search);
    const preselectedSeatIds = urlParams.get('seatIds') ? urlParams.get('seatIds').split(',').map(Number) : [];
    const preselectedCombos = urlParams.get('combos') ? urlParams.get('combos') : '';

    // Init page state
    if (initialShowtimeId > 0) {
        selectedShowtimeId = initialShowtimeId;
        // Find film ID from showtime
        const st = allShowtimes.find(x => x.id === initialShowtimeId);
        if (st) {
            selectedFilmId = st.filmId;
            currentBasePrice = st.basePrice;
        }
        setStep(2);

        // Restore combos selections
        if (preselectedCombos) {
            const combos = preselectedCombos.split(',');
            combos.forEach(c => {
                const pair = c.split(':');
                if (pair.length === 2) {
                    const cid = Number(pair[0]);
                    const qty = Number(pair[1]);
                    if (cid && qty > 0) {
                        comboSelections[cid] = qty;
                        const qtyEl = document.getElementById(`qty-${cid}`);
                        const inputEl = document.getElementById(`input-combo-${cid}`);
                        if (qtyEl) qtyEl.textContent = qty;
                        if (inputEl) inputEl.value = qty;
                    }
                }
            });
        }

        loadSeats(initialShowtimeId);
        restoreHoldAfterReload(initialShowtimeId);
    } else {
        setStep(1);
        setupStep1();
    }

    // Step navigation controller
    function setStep(step) {
        currentStep = step;
        
        // Update stepper UI
        if (stepper) {
            stepper.querySelectorAll('[data-step]').forEach(item => {
                const index = Number(item.dataset.step);
                item.classList.toggle('is-active', index === step);
                item.classList.toggle('is-done', index < step);
                
                // Allow direct clicking on stepper tabs
                item.style.cursor = 'pointer';
                if (!item.hasAttribute('data-click-bound')) {
                    item.setAttribute('data-click-bound', 'true');
                    item.addEventListener('click', () => {
                        const target = Number(item.dataset.step);
                        if (target === 1) {
                            setStep(1);
                        } else if (target === 2 && selectedShowtimeId) {
                            setStep(2);
                            loadSeats(selectedShowtimeId);
                        } else if (target === 3 && currentOrderId) {
                            setStep(3);
                        } else if (target === 4 && currentOrderId) {
                            setStep(4);
                        }
                    });
                }
            });
        }

        // Toggle step containers
        document.querySelectorAll('.booking-step').forEach(container => {
            container.classList.toggle('active', Number(container.dataset.stepId) === step);
        });

        // Toggle action buttons in sidebar
        if (step === 1) {
            btnBack.style.display = 'none';
            btnContinue.textContent = 'Ch\u1ecdn gh\u1ebf';
            btnContinue.disabled = !selectedShowtimeId;
        } else if (step === 2) {
            btnBack.style.display = 'inline-block';
            btnContinue.textContent = 'Ch\u1ecdn th\u1ee9c \u0103n';
            btnContinue.disabled = selectedSeats.length === 0;
        } else if (step === 3) {
            btnBack.style.display = 'inline-block';
            btnContinue.textContent = 'Đi đến thanh toán';
            btnContinue.disabled = false;
            ensureCombosLoaded();
        } else if (step === 4) {
            btnBack.style.display = 'inline-block';
            btnContinue.textContent = (selectedPaymentMethod === 'counter') ? 'X\u00e1c nh\u1eadn \u0111\u1eb7t v\u00e9' : 'Thanh to\u00e1n gi\u1ea3 l\u1eadp';
            btnContinue.disabled = false;
        } else if (step === 5) {
            btnBack.style.display = 'none';
            btnContinue.textContent = 'L\u1ecbch s\u1eed \u0111\u1eb7t v\u00e9';
            btnContinue.disabled = false;
            stopCountdown();
        }

        sideErrorMessage.textContent = '';
        renderSummary();
    }

    // Setup Step 1 Accordion and dynamic options
    function setupStep1() {
        // Location chips listener
        document.querySelectorAll('#locationChips .chip').forEach(chip => {
            chip.addEventListener('click', () => {
                document.querySelectorAll('#locationChips .chip').forEach(c => c.classList.remove('active'));
                chip.classList.add('active');
                filterShowtimes();
            });
        });

        // Film select listeners
        document.querySelectorAll('#filmSelectGrid .film-select-card').forEach(card => {
            card.addEventListener('click', () => {
                document.querySelectorAll('#filmSelectGrid .film-select-card').forEach(c => c.classList.remove('active'));
                card.classList.add('active');
                selectedFilmId = Number(card.dataset.filmId);
                
                // Update sidebar film metadata
                sideFilmPoster.style.backgroundImage = `url('${card.dataset.poster || contextPath + '/assets/img/default-film.jpg'}')`;
                sideFilmTitle.textContent = card.dataset.title;
                sideFilmAge.textContent = card.dataset.age;
                sideFilmAge.style.display = card.dataset.age ? 'inline-block' : 'none';

                // Update accordion header with selected film title badge
                const filmHeaderTitle = document.querySelector('#accordionFilm .accordion-header span:first-child');
                if (filmHeaderTitle) {
                    filmHeaderTitle.innerHTML = '2. Chọn phim <span class="selected-film-badge">✓ ' + card.dataset.title + '</span>';
                }

                // Automatically open showtimes accordion and load dates (keep film selection open as requested)
                document.getElementById('accordionShowtime').classList.remove('closed');

                loadDatesForFilm(selectedFilmId);
            });
        });
    }

    function getDayOfWeekVN(dateStr) {
        if (!dateStr) return '';
        const parts = dateStr.split('-');
        if (parts.length !== 3) return '';
        const dateObj = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
        const days = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
        const today = new Date();
        const todayStr = today.getFullYear() + '-' + String(today.getMonth() + 1).padStart(2, '0') + '-' + String(today.getDate()).padStart(2, '0');
        if (dateStr === todayStr) return 'Hôm nay';
        return days[dateObj.getDay()];
    }

    function loadDatesForFilm(filmId) {
        const sts = allShowtimes.filter(x => x.filmId === filmId);
        const dates = [...new Set(sts.map(x => x.startTime.substring(0, 10)))].sort();
        
        const dateChips = document.getElementById('dateChips');
        if (dateChips) {
            dateChips.innerHTML = '';

            if (dates.length === 0) {
                document.getElementById('showtimeGroupList').innerHTML = '<p class="muted" style="padding: 16px 0;">Phim này hiện chưa có lịch chiếu.</p>';
                return;
            }

            dates.forEach((d, index) => {
                const parts = d.split('-');
                const dayMonthShort = parts.length === 3 ? `${parts[2]}/${parts[1]}` : d;
                const dowText = getDayOfWeekVN(d);

                const chip = document.createElement('div');
                chip.className = 'date-chip-item' + (index === 0 ? ' active' : '');
                chip.innerHTML = `<span class="date-dow">${dowText}</span><span class="date-num">${dayMonthShort}</span>`;
                chip.dataset.date = d;
                chip.addEventListener('click', () => {
                    dateChips.querySelectorAll('.date-chip-item').forEach(c => c.classList.remove('active'));
                    chip.classList.add('active');
                    filterShowtimes();
                });
                dateChips.appendChild(chip);
            });
        }

        // Populates cinema filter select dropdown with all available cinemas
        const cinemaSelect = document.getElementById('cinemaFilterSelect');
        if (cinemaSelect) {
            cinemaSelect.innerHTML = '<option value="0">Tất cả các rạp</option>';
            const cinemaMap = new Map();
            sts.forEach(st => {
                if (st.cinemaId && st.cinemaName) {
                    cinemaMap.set(st.cinemaId, st.cinemaName);
                }
            });
            cinemaMap.forEach((name, id) => {
                const opt = document.createElement('option');
                opt.value = id;
                opt.textContent = '📍 ' + name;
                cinemaSelect.appendChild(opt);
            });

            cinemaSelect.onchange = () => {
                filterShowtimes();
            };
        }

        filterShowtimes();
    }

    function filterShowtimes() {
        if (!selectedFilmId) return;

        const activeDateChip = document.querySelector('#dateChips .date-chip-item.active') || document.querySelector('#dateChips .chip.active');
        if (!activeDateChip) return;
        const selectedDate = activeDateChip.dataset.date;

        const locAccordion = document.getElementById('accordionLoc');
        const isLocVisible = locAccordion && locAccordion.style.display !== 'none';
        const activeLocChip = isLocVisible ? document.querySelector('#locationChips .chip.active') : null;
        const selectedCityId = activeLocChip ? Number(activeLocChip.dataset.cityId || 0) : 0;

        const cinemaSelect = document.getElementById('cinemaFilterSelect');
        const selectedCinemaId = cinemaSelect ? Number(cinemaSelect.value || 0) : 0;

        // Filter showtimes matching film, date, cityId and optional cinemaId filter
        const sts = allShowtimes.filter(x => 
            x.filmId === selectedFilmId && 
            x.startTime.startsWith(selectedDate) &&
            (!selectedCityId || x.cityId === selectedCityId) &&
            (!selectedCinemaId || x.cinemaId === selectedCinemaId)
        );

        const listContainer = document.getElementById('showtimeGroupList');
        if (!listContainer) return;
        listContainer.innerHTML = '';

        if (sts.length === 0) {
            listContainer.innerHTML = '<p class="muted" style="padding:16px 0;">Không có suất chiếu phù hợp cho bộ lọc này.</p>';
            return;
        }

        // Group by cinema -> then subgroup by version format (e.g. IMAX 2D Lồng tiếng / 2D Thuyết minh)
        const cinemasMap = {};
        sts.forEach(st => {
            if (!cinemasMap[st.cinemaId]) {
                cinemasMap[st.cinemaId] = {
                    name: st.cinemaName,
                    versionGroups: {}
                };
            }
            const verLabel = st.roomFormatLabel || st.formatVersion || (st.format + ' ' + st.version);
            if (!cinemasMap[st.cinemaId].versionGroups[verLabel]) {
                cinemasMap[st.cinemaId].versionGroups[verLabel] = [];
            }
            cinemasMap[st.cinemaId].versionGroups[verLabel].push(st);
        });

        Object.values(cinemasMap).forEach(cinemaGroup => {
            const cardBlock = document.createElement('div');
            cardBlock.className = 'cinema-card-block';

            const header = document.createElement('div');
            header.className = 'cinema-card-header';
            header.innerHTML = `<span class="cinema-icon" style="color:#ff7a00;">📍</span> ${cinemaGroup.name}`;
            cardBlock.appendChild(header);

            Object.entries(cinemaGroup.versionGroups).forEach(([verLabel, times]) => {
                times.sort((a, b) => a.startTime.localeCompare(b.startTime));

                const versionDiv = document.createElement('div');
                versionDiv.className = 'version-showtime-group';

                const labelDiv = document.createElement('div');
                labelDiv.className = 'version-label-text';
                labelDiv.style.display = 'flex';
                labelDiv.style.alignItems = 'center';
                labelDiv.style.justifyContent = 'space-between';

                const labelTitle = document.createElement('span');
                labelTitle.innerHTML = `<span style="color:#ff7a00; margin-right:4px;">🎥</span> <strong>${verLabel}</strong>`;
                labelDiv.appendChild(labelTitle);

                const firstStInGroup = times[0];
                const isGroupRoomActive = firstStInGroup && (firstStInGroup.isRoomActive !== false);

                if (!isGroupRoomActive) {
                    const statusBadge = document.createElement('span');
                    statusBadge.style.fontSize = '0.78rem';
                    statusBadge.style.padding = '2px 8px';
                    statusBadge.style.borderRadius = '4px';
                    statusBadge.style.fontWeight = '700';
                    statusBadge.style.background = '#fef2f2';
                    statusBadge.style.color = '#dc2626';
                    statusBadge.style.border = '1px solid #fca5a5';
                    statusBadge.textContent = '⚠️ Phòng ngưng hoạt động';
                    labelDiv.appendChild(statusBadge);
                }
                versionDiv.appendChild(labelDiv);

                const timesDiv = document.createElement('div');
                timesDiv.className = 'showtimes-flex-list';

                times.forEach(st => {
                    const btn = document.createElement('button');
                    btn.type = 'button';
                    if (st.isRoomActive === false) {
                        btn.className = 'showtime-btn is-disabled';
                        btn.style.background = '#f1f5f9';
                        btn.style.color = '#94a3b8';
                        btn.style.border = '1px solid #cbd5e1';
                        btn.style.cursor = 'not-allowed';
                        btn.style.opacity = '0.65';
                        btn.style.textDecoration = 'line-through';
                        btn.title = `Ph\u00f2ng chi\u1ebfu ${st.roomName} \u0111ang t\u1ea1m ng\u01b0ng ho\u1ea1t \u0111\u1ed9ng. Kh\u00f4ng th\u1ec3 \u0111\u1eb7t v\u00e9 m\u1edbi.`;
                        btn.textContent = (st.onlyTime || (st.displayTime ? st.displayTime.split(' ').pop() : st.startTime)) + ' (Ng\u01b0ng)';
                        btn.addEventListener('click', (e) => {
                            e.preventDefault();
                            alert(`Ph\u00f2ng chi\u1ebfu "${st.roomName}" \u0111ang t\u1ea1m ng\u01b0ng ho\u1ea1t \u0111\u1ed9ng. Kh\u00f4ng th\u1ec3 \u0111\u1eb7t v\u00e9 m\u1edbi.`);
                        });
                    } else {
                        btn.className = 'showtime-btn' + (selectedShowtimeId === st.id ? ' active' : '');
                        btn.setAttribute('data-showtime-id', st.id);
                        btn.textContent = st.onlyTime || (st.displayTime ? st.displayTime.split(' ').pop() : st.startTime);
                        btn.title = `${st.cinemaName} - ${verLabel} lúc ${st.displayTime}`;

                        btn.addEventListener('click', () => {
                            if (selectedShowtimeId === st.id) {
                                btn.classList.remove('active');
                                deselectShowtime();
                            } else {
                                selectShowtime(st);
                            }
                        });
                    }
                    timesDiv.appendChild(btn);
                });

                versionDiv.appendChild(timesDiv);
                cardBlock.appendChild(versionDiv);
            });

            listContainer.appendChild(cardBlock);
        });
    }

    function deselectShowtime() {
        selectedShowtimeId = null;
        currentBasePrice = 0;
        document.querySelectorAll('.showtime-btn').forEach(b => b.classList.remove('active'));
        if (sideCinemaInfo) {
            sideCinemaInfo.innerHTML = `<span>Vui lòng chọn rạp và suất chiếu ở bước 1.</span>`;
        }
        if (document.getElementById('sideFilmFormatVer')) {
            document.getElementById('sideFilmFormatVer').textContent = '';
        }
        selectedSeats = [];
        currentVisualSeats = [];
        selectedPreviewSeat = null;
        updateSeat3dButton();
        seatVersionEtag = null;
        if (seatPollInterval) {
            clearInterval(seatPollInterval);
            seatPollInterval = null;
        }
        const seatGrid = document.getElementById('seatGridNew');
        if (seatGrid) {
            seatGrid.innerHTML = '';
        }
        btnContinue.disabled = true;
        renderSummary();
    }

    function selectShowtime(st) {
        selectedShowtimeId = st.id;
        selectedFilmId = Number(st.filmId || selectedFilmId || 0);
        currentBasePrice = st.basePrice;
        
        document.querySelectorAll('.showtime-btn').forEach(b => {
            const bId = b.getAttribute('data-showtime-id');
            if (bId && parseInt(bId, 10) === st.id) {
                b.classList.add('active');
            } else {
                b.classList.remove('active');
            }
        });

        if (document.getElementById('sideFilmFormatVer')) {
            document.getElementById('sideFilmFormatVer').textContent = st.roomFormatLabel || st.formatVersion || '2D Phụ Đề';
        }

        // Update sidebar showtime info
        sideCinemaInfo.innerHTML = `
            <strong>${st.cinemaName}</strong>
            <span>${st.roomName} (${st.formatVersion || '2D'})</span>
            <div style="margin-top: 6px; font-weight: 700; color: var(--color-secondary);">
                📅 ${st.displayTime}
            </div>
        `;

        btnContinue.disabled = false;
        sideErrorMessage.textContent = '';

        // Auto transition to Step 2 (Chọn ghế) and load seat map immediately!
        setStep(2);
        loadSeats(st.id);
    }

    // Convert two physical records of a couple seat (odd & even) into one merged visual cell
    function inflatePhysicalSeats(physicalSeats) {
        const grouped = {};
        physicalSeats.forEach(seat => {
            if (!grouped[seat.rowLabel]) grouped[seat.rowLabel] = [];
            grouped[seat.rowLabel].push(Object.assign({}, seat));
        });
        const visual = [];
        Object.keys(grouped).sort().forEach(rowLabel => {
            const row = grouped[rowLabel].sort((a, b) => a.seatNumber - b.seatNumber);
            const consumed = {};
            row.forEach(seat => {
                if (consumed[seat.seatNumber]) return;
                if (seat.seatType === 'couple') {
                    const firstNumber = seat.seatNumber % 2 === 1 ? seat.seatNumber : seat.seatNumber - 1;
                    const secondNumber = firstNumber + 1;
                    const first = row.find(item => item.seatNumber === firstNumber && item.seatType === 'couple');
                    const second = row.find(item => item.seatNumber === secondNumber && item.seatType === 'couple');
                    if (first && second) {
                        const isHeldByMe = (first.viewerState === 'heldByMe' || second.viewerState === 'heldByMe');
                        const isHeldByOther = (first.viewerState === 'heldByOther' || second.viewerState === 'heldByOther');
                        const isBooked = (first.viewerState === 'booked' || second.viewerState === 'booked' || first.status === 'booked' || second.status === 'booked');
                        const isMaintenance = (first.status === 'maintenance' || second.status === 'maintenance' || first.seatType === 'maintenance' || second.seatType === 'maintenance');
                        
                        const viewerState = isHeldByMe ? 'heldByMe' : (isHeldByOther ? 'heldByOther' : (isBooked ? 'booked' : 'available'));
                        
                        visual.push({
                            rowLabel: rowLabel,
                            seatNumber: firstNumber,
                            mergedSeatNumber: secondNumber,
                            seatType: 'couple',
                            seatKey: rowLabel + firstNumber + '-' + secondNumber,
                            physicalSeats: [first, second],
                            selectable: first.selectable && second.selectable && !isMaintenance,
                            isMaintenance: isMaintenance,
                            status: isBooked ? 'booked' : (isHeldByMe || isHeldByOther ? 'held' : 'available'),
                            viewerState: viewerState
                        });
                        consumed[firstNumber] = true;
                        consumed[secondNumber] = true;
                        return;
                    }
                }
                visual.push(Object.assign({}, seat, {
                    mergedSeatNumber: null,
                    physicalSeats: [seat],
                    isMaintenance: seat.status === 'maintenance' || seat.seatType === 'maintenance'
                }));
                consumed[seat.seatNumber] = true;
            });
        });
        return visual;
    }

    // Step 2: Seat Map loading and rendering
    async function loadSeats(showtimeId) {
        try {
            const response = await fetch(contextPath + '/showtimes/' + showtimeId + '/seats', {
                headers: { Accept: 'application/json' }
            });
            const data = await response.json();
            
            const grid = document.getElementById('seatGridNew');
            grid.innerHTML = '';
            selectedSeats = [];
            selectedPreviewSeat = null;
            updateSeat3dButton();
            btnContinue.disabled = true;

            const visualSeats = inflatePhysicalSeats(data.items);
            currentVisualSeats = visualSeats;

            // Group visual seats by Row Label
            const rowMap = {};
            visualSeats.forEach(seat => {
                if (!rowMap[seat.rowLabel]) {
                    rowMap[seat.rowLabel] = [];
                }
                rowMap[seat.rowLabel].push(seat);
            });

            // Calculate maxCols across all rows to align grid columns
            let maxCols = 0;
            visualSeats.forEach(s => {
                if (s.seatNumber > maxCols) maxCols = s.seatNumber;
                if (s.mergedSeatNumber && s.mergedSeatNumber > maxCols) maxCols = s.mergedSeatNumber;
            });
            maxCols = Math.max(maxCols, 12);

            // Sort rows descending J down to A
            const sortedRows = Object.keys(rowMap).sort().reverse();

            sortedRows.forEach(rowLabel => {
                const rowDiv = document.createElement('div');
                rowDiv.className = 'seat-row';

                // Left row label
                const leftLabel = document.createElement('span');
                leftLabel.className = 'row-label-cell';
                leftLabel.textContent = rowLabel;
                rowDiv.appendChild(leftLabel);

                // Seats cell buttons container
                const buttonsGrid = document.createElement('div');
                buttonsGrid.className = 'seat-buttons-grid';

                const rowSeats = rowMap[rowLabel] || [];

                for (let colNum = 1; colNum <= maxCols; colNum++) {
                    const seatObj = rowSeats.find(s => s.seatNumber === colNum);
                    if (!seatObj) {
                        const spacer = document.createElement('div');
                        spacer.className = 'seat-slot-spacer';
                        buttonsGrid.appendChild(spacer);
                        continue;
                    }

                    const isMaintenance = seatObj.isMaintenance;
                    const viewerState = seatObj.viewerState || (seatObj.status === 'held' ? 'heldByOther' : seatObj.status);
                    const btn = document.createElement('button');
                    btn.type = 'button';
                    const viewerClass = viewerState === 'heldByMe' ? 'held held-by-me'
                        : (viewerState === 'heldByOther' ? 'held held-by-other'
                        : (viewerState === 'booked' ? 'booked' : ''));
                    btn.className = `seat-cell ${seatObj.seatType} ${viewerClass} ` +
                        (isMaintenance ? 'booked maintenance-seat' : '');
                    btn.setAttribute('data-seat-key', seatObj.seatKey);

                    if (isMaintenance) {
                        btn.style.background = '#e2e8f0';
                        btn.style.borderColor = '#94a3b8';
                        btn.style.color = '#64748b';
                        btn.style.cursor = 'not-allowed';
                    }

                    if (seatObj.seatType === 'couple') {
                        btn.textContent = seatObj.seatNumber + '-' + seatObj.mergedSeatNumber;
                    } else {
                        btn.textContent = seatObj.seatNumber;
                    }

                    const stateLabel = viewerState === 'heldByMe' ? 'Bạn đang giữ — bấm để tiếp tục đơn hiện tại'
                        : (viewerState === 'heldByOther' ? 'Khách khác đang giữ'
                        : (viewerState === 'booked' ? 'Đã bán' : (seatObj.seatType === 'couple' ? 'Ghế đôi' : seatObj.seatType)));
                    btn.title = isMaintenance ? `Ghế ${seatObj.seatKey} (Đang bảo trì)` : `Ghế ${seatObj.seatKey} (${stateLabel})`;
                    btn.setAttribute('aria-label', btn.title);

                    // Check if preselected from URL or state
                    const hasPreselected = seatObj.physicalSeats.some(ps => preselectedSeatIds.includes(ps.id));
                    if (hasPreselected) {
                        btn.classList.add('selected');
                        seatObj.physicalSeats.forEach(ps => {
                            if (!selectedSeats.some(s => s.id === ps.id)) selectedSeats.push(ps);
                        });
                        if (!selectedPreviewSeat) selectedPreviewSeat = seatObj;
                    }

                    if (!seatObj.selectable || isMaintenance) {
                        btn.addEventListener('click', () => {
                            sideErrorMessage.textContent = isMaintenance 
                                ? `Ghế ${seatObj.seatKey} đang trong quá trình bảo trì, không thể chọn.`
                                : `Ghế ${seatObj.seatKey} không khả dụng.`;
                        });
                    } else {
                        btn.addEventListener('click', () => {
                            let physicalsToToggle = [...seatObj.physicalSeats];
                            const heldItem = seatObj.physicalSeats.find(ps => ps.heldOrderId);
                            if (heldItem && heldItem.viewerState === 'heldByMe') {
                                physicalsToToggle = data.items.filter(item => item.heldOrderId === heldItem.heldOrderId);
                            }

                            const isCurrentlySelected = btn.classList.contains('selected');
                            const physIdsToToggle = physicalsToToggle.map(ps => ps.id);

                            if (isCurrentlySelected) {
                                selectedSeats = selectedSeats.filter(s => !physIdsToToggle.includes(s.id));
                            } else {
                                physicalsToToggle.forEach(ps => {
                                    if (!selectedSeats.some(s => s.id === ps.id)) {
                                        selectedSeats.push(ps);
                                    }
                                });
                                selectedPreviewSeat = seatObj;
                            }

                            // Refresh selected status on all visual buttons in grid
                            document.querySelectorAll('.seat-cell').forEach(cellBtn => {
                                const key = cellBtn.getAttribute('data-seat-key');
                                const vSeat = visualSeats.find(vs => vs.seatKey === key);
                                if (vSeat) {
                                    const allSel = vSeat.physicalSeats.length > 0 && vSeat.physicalSeats.every(ps => selectedSeats.some(s => s.id === ps.id));
                                    if (allSel) {
                                        cellBtn.classList.add('selected');
                                    } else {
                                        cellBtn.classList.remove('selected');
                                    }
                                }
                            });

                            if (isCurrentlySelected) selectedPreviewSeat = selectedVisualSeat(visualSeats);
                            updateSeat3dButton();
                            btnContinue.disabled = selectedSeats.length === 0;
                            renderSummary();
                        });
                    }

                    buttonsGrid.appendChild(btn);

                    if (seatObj.seatType === 'couple' && seatObj.mergedSeatNumber === colNum + 1) {
                        colNum++; // Skip next column index for merged couple seat cell
                    }
                }

                rowDiv.appendChild(buttonsGrid);

                // Right row label
                const rightLabel = document.createElement('span');
                rightLabel.className = 'row-label-cell';
                rightLabel.textContent = rowLabel;
                rowDiv.appendChild(rightLabel);

                grid.appendChild(rowDiv);
            });

            if (selectedSeats.length > 0) {
                btnContinue.disabled = false;
                renderSummary();
            }
            updateSeat3dButton();
            startSeatPolling(showtimeId);
        } catch (ex) {
            currentVisualSeats = [];
            selectedPreviewSeat = null;
            updateSeat3dButton();
            sideErrorMessage.textContent = 'Không thể tải sơ đồ ghế.';
        }
    }

    function startSeatPolling(showtimeId) {
        if (seatPollInterval) clearInterval(seatPollInterval);
        seatVersionEtag = null;
        const poll = async function () {
            if (currentStep !== 2 || selectedShowtimeId !== showtimeId) return;
            const headers = { Accept: 'application/json' };
            if (seatVersionEtag) headers['If-None-Match'] = seatVersionEtag;
            try {
                const response = await fetch(
                    contextPath + '/api/v1/showtimes/' + showtimeId + '/seats/version',
                    { headers: headers, cache: 'no-store' }
                );
                if (response.status === 304) return;
                if (!response.ok) return;
                const nextEtag = response.headers.get('ETag');
                const changed = seatVersionEtag && nextEtag && seatVersionEtag !== nextEtag;
                seatVersionEtag = nextEtag;
                if (changed) {
                    const hadSelection = selectedSeats.length > 0;
                    await loadSeats(showtimeId);
                    if (hadSelection) {
                        sideErrorMessage.textContent = 'Sơ đồ ghế vừa thay đổi; lựa chọn đã được đồng bộ để tránh trùng ghế.';
                    }
                }
            } catch (ex) {
                // Polling is best-effort; the normal booking request still validates every seat.
            }
        };
        poll();
        seatPollInterval = setInterval(poll, 5000);
    }

    window.addEventListener('beforeunload', function () {
        if (seatPollInterval) clearInterval(seatPollInterval);
    });

    async function ensureCombosLoaded() {
        const comboContainer = document.querySelector('.combo-list-new');
        if (!comboContainer) return;
        const rows = comboContainer.querySelectorAll('.combo-row-new');
        if (rows.length > 0) return;

        try {
            const res = await fetch(contextPath + '/api/v1/combos', {
                headers: { 'Accept': 'application/json' }
            });
            if (!res.ok) return;
            const data = await res.json();
            const combos = (data && data.data) ? data.data : (Array.isArray(data) ? data : []);
            if (!combos || combos.length === 0) return;

            comboContainer.innerHTML = '';
            combos.forEach(c => {
                const rawImg = c.image || c.imageUrl || '';
                const cImg = rawImg ? (rawImg.startsWith('http') || rawImg.startsWith('/') ? (rawImg.startsWith('/') ? contextPath + rawImg : rawImg) : contextPath + '/' + rawImg) : '';
                const bgStyle = cImg ? `style="background-image: url('${cImg}');"` : '';
                const iconContent = cImg ? '' : '🍿';
                const row = document.createElement('div');
                row.className = 'combo-row-new';
                row.innerHTML = `
                    <div class="combo-img" ${bgStyle}>${iconContent}</div>
                    <div class="combo-details">
                        <span class="combo-title">${c.name}</span>
                        <span class="combo-desc">${c.description || ''}</span>
                        <span class="combo-price">${money(c.price)}</span>
                    </div>
                    <div class="qty-controls">
                        <button type="button" class="qty-btn btn-combo-minus" data-combo-id="${c.id}" data-price="${c.price}">-</button>
                        <span class="qty-val" id="qty-${c.id}">0</span>
                        <button type="button" class="qty-btn btn-combo-plus" data-combo-id="${c.id}" data-price="${c.price}">+</button>
                        <input type="hidden" class="combo-input-new" data-combo-id="${c.id}" data-price="${c.price}" data-name="${c.name}" id="input-combo-${c.id}" value="0">
                    </div>
                `;
                const minusBtn = row.querySelector('.btn-combo-minus');
                const plusBtn = row.querySelector('.btn-combo-plus');
                minusBtn.addEventListener('click', () => adjustCombo(c.id, -1, c.price));
                plusBtn.addEventListener('click', () => adjustCombo(c.id, 1, c.price));
                comboContainer.appendChild(row);
            });
        } catch (ex) {
            console.error('Failed to load fallback combos', ex);
        }
    }

    // Step 3: Food Combos adjustment
    window.adjustCombo = function (comboId, delta, price) {
        let currentVal = Number(document.getElementById(`qty-${comboId}`).textContent);
        let newVal = Math.max(0, currentVal + delta);
        document.getElementById(`qty-${comboId}`).textContent = newVal;
        document.getElementById(`input-combo-${comboId}`).value = newVal;

        comboSelections[comboId] = newVal;
        renderSummary();
    };

    // Step 4: Payment select
    window.selectPayment = function (method, element) {
        document.querySelectorAll('.payment-option').forEach(opt => opt.classList.remove('active'));
        element.classList.add('active');
        selectedPaymentMethod = method;
        document.getElementById('paymentMethodNew').value = method;
        
        if (currentStep === 4) {
            btnContinue.textContent = (method === 'counter') ? 'Xác nhận đặt vé' : 'Thanh toán giả lập';
        }
    };

    // Promotion code simulation/validation
    const applyPromoBtn = document.getElementById('applyPromoBtn');
    if (applyPromoBtn) {
        applyPromoBtn.addEventListener('click', async () => {
            const code = document.getElementById('promoCodeNew').value.trim().toUpperCase();
            const msg = document.getElementById('promoMessage');
            if (!code) {
                msg.textContent = '';
                appliedPromotion = null;
                renderSummary();
                return;
            }
            applyPromoBtn.disabled = true;
            applyPromoBtn.textContent = 'Đang kiểm tra...';
            try {
                // BUG-16: moi loi goi doc JSON deu phai XIN JSON, de duong loi cua filter tra JSON
                // chu khong phai HTML — res.json() se nem loi parse neu nhan trang login.
                const res = await fetch(contextPath + '/promotions/validate?code=' + encodeURIComponent(code),
                    { headers: { 'Accept': 'application/json' } });
                const data = await res.json();
                if (data.error) {
                    msg.textContent = data.message;
                    msg.style.color = 'var(--color-danger)';
                    appliedPromotion = null;
                } else {
                    msg.textContent = `Áp dụng mã ${data.code} thành công! Giảm ${data.discountPercent}%.`;
                    msg.style.color = 'var(--color-success)';
                    appliedPromotion = data;
                }
            } catch (ex) {
                msg.textContent = 'Lỗi kết nối khi kiểm tra khuyến mãi.';
                msg.style.color = 'var(--color-danger)';
                appliedPromotion = null;
            } finally {
                applyPromoBtn.disabled = false;
                applyPromoBtn.textContent = 'Áp dụng';
                renderSummary();
            }
        });
    }

    // Order summary rendering
    function renderSummary() {
        if (!sideDetailsList) return;

        sideDetailsList.innerHTML = '';
        let total = 0;

        // Render seats
        if (selectedSeats.length > 0) {
            const seatKeys = selectedSeats.map(s => s.seatKey).join(', ');
            let seatCost = selectedSeats.reduce((sum, s) => sum + currentBasePrice + Number(s.extraFee || 0), 0);
            total += seatCost;

            const row = document.createElement('div');
            row.className = 'detail-item-row';
            row.innerHTML = `<span>Gh\u1ebf (${seatKeys})</span><strong class="detail-item-val">${money(seatCost)}</strong>`;
            sideDetailsList.appendChild(row);
        } else {
            const row = document.createElement('div');
            row.className = 'detail-item-row';
            row.innerHTML = `<span>V\u00e9 xem phim</span><span class="detail-item-val">Ch\u01b0a ch\u1ecdn gh\u1ebf</span>`;
            sideDetailsList.appendChild(row);
        }

        // Render combos
        let comboCost = 0;
        document.querySelectorAll('.combo-input-new').forEach(input => {
            const qty = Number(input.value || 0);
            if (qty > 0) {
                const name = input.dataset.name;
                const price = Number(input.dataset.price);
                const lineTotal = price * qty;
                comboCost += lineTotal;
                total += lineTotal;

                const row = document.createElement('div');
                row.className = 'detail-item-row';
                row.innerHTML = `<span>${qty}x ${name}</span><strong class="detail-item-val">${money(lineTotal)}</strong>`;
                sideDetailsList.appendChild(row);
            }
        });

        // Apply discount from state
        if (appliedPromotion && total > 0) {
            const pct = Number(appliedPromotion.discountPercent || 0) / 100;
            const maxD = Number(appliedPromotion.maxDiscount || 50000);
            let discount = Math.min(maxD, total * pct);
            total -= discount;

            const row = document.createElement('div');
            row.className = 'detail-item-row';
            row.style.color = 'var(--color-success)';
            row.innerHTML = `<span>Khuyến mãi (${appliedPromotion.code})</span><strong class="detail-item-val">-${money(discount)}</strong>`;
            sideDetailsList.appendChild(row);
        }

        sideTotalPrice.textContent = money(total);
    }

    function buildComboItems() {
        return Object.entries(comboSelections)
            .filter(([_, qty]) => qty > 0)
            .map(([id, qty]) => `${id}:${qty}`)
            .join(',');
    }

    // ------------------------------------------------------------------
    // Đồng hồ giữ ghế (B3)
    //
    // Trước đây khối này đếm ngược 600 giây độc lập, không liên quan gì tới
    // ShowtimeSeats.HeldUntil trong DB. Hệ quả: F5 lại trang là đồng hồ chạy lại từ đầu
    // dù ghế sắp hết hạn, còn khi sweeper đã thu ghế thì UI vẫn đếm tiếp và người dùng
    // bấm thanh toán để nhận 409.
    //
    // Nay số giây còn lại do SQL Server tính (DATEDIFF trên GETDATE()), đồng bộ lại mỗi
    // 30 giây, và hết hạn thì khoá nút thanh toán ngay.
    // ------------------------------------------------------------------
    const HOLD_SYNC_MS = 30000;
    const HOLD_STORE_KEY = 'cinebook.holdOrder';
    let holdSyncInterval = null;
    let holdSecondsLeft = 0;
    let holdExpired = false;

    // Nhớ đơn đang giữ ghế để F5 xong còn hỏi lại server được hạn thật.
    // sessionStorage có thể ném ở chế độ ẩn danh — hỏng thì chỉ mất phần khôi phục UI,
    // hạn giữ ghế thật vẫn do DB quyết định, nên đây là đường lùi an toàn.
    function rememberOrder(orderId, showtimeId) {
        try {
            sessionStorage.setItem(HOLD_STORE_KEY, JSON.stringify({ orderId: orderId, showtimeId: showtimeId }));
        } catch (ex) {
            /* không khôi phục được sau F5, chấp nhận được */
        }
    }

    function forgetOrder() {
        try {
            sessionStorage.removeItem(HOLD_STORE_KEY);
            sessionStorage.removeItem(IDEMPOTENCY_STORE_KEY);
        } catch (ex) {
            /* như trên */
        }
    }

    // ------------------------------------------------------------------
    // C.1 — khoá idempotency do client cấp.
    //
    // Backend đã nhận X-Idempotency-Key ở cả /orders và /orders/{id}/pay từ lâu, nhưng
    // KHÔNG client nào gửi, nên Orders.IdempotencyKey luôn NULL và cả lớp chống trùng là
    // mã chết: khách bấm hai lần hoặc rớt mạng rồi thử lại vẫn gặp đúng triệu chứng cũ.
    //
    // Một khoá cho MỘT LẦN ĐẶT VÉ, dùng lại cho cả bước tạo đơn lẫn bước thanh toán, và
    // sống qua F5 — nếu sinh khoá mới mỗi lần gửi thì server không nhận ra đó là lần thử
    // lại và ta chẳng chống được gì. Khoá bị quên đi khi đơn kết thúc (forgetOrder).
    // ------------------------------------------------------------------
    const IDEMPOTENCY_STORE_KEY = 'cinebook.bookingKey';

    function newKey() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        // Trình duyệt cũ / ngữ cảnh không bảo mật: randomUUID không tồn tại. Khoá chỉ cần
        // duy nhất chứ không cần khó đoán — server đã buộc khoá phải khớp chủ đơn (A.2).
        return 'cb-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
    }

    function bookingKey() {
        try {
            let key = sessionStorage.getItem(IDEMPOTENCY_STORE_KEY);
            if (!key) {
                key = newKey();
                sessionStorage.setItem(IDEMPOTENCY_STORE_KEY, key);
            }
            return key;
        } catch (ex) {
            // Chế độ ẩn danh chặn sessionStorage: vẫn gửi một khoá cho lần gửi này. Mất khả
            // năng nhận diện retry sau F5, nhưng double-click trong cùng một trang vẫn được
            // chặn vì cùng dùng lại biến này.
            fallbackBookingKey = fallbackBookingKey || newKey();
            return fallbackBookingKey;
        }
    }

    let fallbackBookingKey = null;

    function recallOrderId(showtimeId) {
        try {
            const raw = sessionStorage.getItem(HOLD_STORE_KEY);
            if (!raw) return null;
            const saved = JSON.parse(raw);
            return (saved && Number(saved.showtimeId) === Number(showtimeId)) ? Number(saved.orderId) : null;
        } catch (ex) {
            return null;
        }
    }

    // Sau khi F5: hỏi server xem đơn cũ còn giữ ghế không. Còn thì quay lại đúng bước và
    // đếm tiếp theo số giây thật, hết hạn thì quên đơn đi.
    async function restoreHoldAfterReload(showtimeId) {
        const savedOrderId = recallOrderId(showtimeId);
        if (!savedOrderId) return;
        currentOrderId = savedOrderId;
        const status = await fetchHoldStatus();
        if (!status || status.expired) {
            forgetOrder();
            currentOrderId = null;
            return;
        }
        setStep(3);
        startCountdown();
    }

    function holdTimerElements() {
        return [document.getElementById('countdownTimer'), document.getElementById('countdownTimerPay')]
            .filter(Boolean);
    }

    function setHoldText(text) {
        holdTimerElements().forEach(el => { el.textContent = text; });
    }

    // Trả về payload {orderId, heldUntil, remainingSeconds, expired, ...} hoặc null nếu không hỏi được.
    async function fetchHoldStatus() {
        if (!currentOrderId) return null;
        try {
            const res = await fetch(`${contextPath}/api/v1/orders/${currentOrderId}/hold`, {
                headers: { Accept: 'application/json' }
            });
            if (!res.ok) return null;
            const body = await res.json();
            return (body && body.data) ? body.data : null;
        } catch (ex) {
            // Mất mạng tạm thời: giữ nguyên đồng hồ đang chạy, vòng đồng bộ sau sẽ sửa lại.
            return null;
        }
    }

    function renderCountdown() {
        const m = Math.floor(Math.max(0, holdSecondsLeft) / 60).toString().padStart(2, '0');
        const s = (Math.max(0, holdSecondsLeft) % 60).toString().padStart(2, '0');
        setHoldText(`Thời gian giữ ghế: ${m}:${s}`);
    }

    function onHoldExpired() {
        if (holdExpired) return;
        holdExpired = true;
        stopCountdown();
        forgetOrder();
        setHoldText('Thời gian giữ ghế đã hết!');
        // Khoá nút thanh toán trước khi báo, để không có cú bấm nào lọt qua.
        btnContinue.disabled = true;
        sideErrorMessage.textContent = 'Ghế đã được trả lại cho người khác vì quá hạn giữ. Vui lòng chọn lại.';
        alert('Thời gian giữ ghế đã hết. Ghế đã được trả lại, vui lòng đặt lại.');
        window.location.reload();
    }

    async function syncHoldFromServer() {
        const status = await fetchHoldStatus();
        if (!status) return false;
        if (status.expired) {
            onHoldExpired();
            return true;
        }
        holdSecondsLeft = Number(status.remainingSeconds || 0);
        renderCountdown();
        return true;
    }

    async function startCountdown() {
        stopCountdown();
        holdExpired = false;
        setHoldText('Đang đồng bộ hạn giữ ghế…');

        // Mốc đầu tiên lấy từ server; nếu không hỏi được thì mới tạm dùng hạn giữ mặc định.
        const synced = await syncHoldFromServer();
        if (holdExpired) return;
        if (!synced) {
            holdSecondsLeft = holdFallbackSeconds;
            renderCountdown();
        }

        countdownInterval = setInterval(() => {
            holdSecondsLeft--;
            if (holdSecondsLeft <= 0) {
                onHoldExpired();
            } else {
                renderCountdown();
            }
        }, 1000);

        holdSyncInterval = setInterval(syncHoldFromServer, HOLD_SYNC_MS);
    }

    function stopCountdown() {
        if (countdownInterval) {
            clearInterval(countdownInterval);
            countdownInterval = null;
        }
        if (holdSyncInterval) {
            clearInterval(holdSyncInterval);
            holdSyncInterval = null;
        }
    }

    // Sidebar button clicks (Continue & Back)
    btnContinue.addEventListener('click', async () => {
        if (currentStep === 1) {
            if (!selectedShowtimeId) {
                sideErrorMessage.textContent = 'Vui lòng chọn suất chiếu trước.';
                return;
            }
            setStep(2);
            loadSeats(selectedShowtimeId);
        } else if (currentStep === 2) {
            if (selectedSeats.length === 0) {
                sideErrorMessage.textContent = 'Vui lòng chọn ít nhất một ghế.';
                return;
            }
            if (!isLoggedIn) {
                sideErrorMessage.textContent = 'Đang chuyển hướng sang đăng nhập...';
                setTimeout(() => {
                    const seatIdsCsv = selectedSeats.map(s => s.id).join(',');
                    const returnUrl = encodeURIComponent('/booking?showtimeId=' + selectedShowtimeId + '&seatIds=' + seatIdsCsv);
                    window.location.href = contextPath + '/login?returnUrl=' + returnUrl;
                }, 1000);
                return;
            }

            const heldOrderIds = [...new Set(selectedSeats
                .filter(seat => seat.viewerState === 'heldByMe' && seat.heldOrderId)
                .map(seat => seat.heldOrderId))];
            if (heldOrderIds.length === 1 && selectedSeats.every(seat => seat.heldOrderId === heldOrderIds[0])) {
                currentOrderId = heldOrderIds[0];
                rememberOrder(currentOrderId, selectedShowtimeId);
                sideErrorMessage.textContent = 'Đã khôi phục đơn đang giữ ghế của bạn.';
                setStep(3);
                startCountdown();
                return;
            }
            
            btnContinue.disabled = true;
            btnContinue.textContent = 'Đang giữ ghế...';
            try {
                const seatsCsv = selectedSeats.map(s => s.id).join(',');
                const orderRes = await fetch(contextPath + '/orders', {
                    method: 'POST',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                        'X-CSRF-Token': csrfToken(),
                        'X-Idempotency-Key': bookingKey()
                    },
                    body: new URLSearchParams({
                        showtimeId: String(selectedShowtimeId),
                        seatIds: seatsCsv,
                        comboItems: '',
                        promotionCode: '',
                        paymentMethod: 'card'
                    })
                });
                const orderData = await orderRes.json();
                if (orderData.error) {
                    sideErrorMessage.textContent = orderData.message;
                    btnContinue.disabled = false;
                    btnContinue.textContent = 'Chọn thức ăn';
                    return;
                }
                currentOrderId = orderData.orderId;
                rememberOrder(currentOrderId, selectedShowtimeId);
                setStep(3);
                startCountdown();
            } catch (err) {
                sideErrorMessage.textContent = 'Lỗi kết nối khi giữ ghế.';
            } finally {
                btnContinue.disabled = false;
                if (currentStep === 2) {
                    btnContinue.textContent = 'Chọn thức ăn';
                }
            }
        } else if (currentStep === 3) {
            setStep(4);
        } else if (currentStep === 4) {
            btnContinue.disabled = true;
            const originalText = btnContinue.textContent;
            btnContinue.textContent = 'Đang xử lý...';
            
            const promo = appliedPromotion ? appliedPromotion.code : '';
            const comboCsv = buildComboItems();

            try {
                // Call pay endpoint directly with combo, promo and paymentMethod parameters
                const payRes = await fetch(contextPath + '/orders/' + currentOrderId + '/pay', {
                    method: 'POST',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                        'X-CSRF-Token': csrfToken(),
                        'X-Idempotency-Key': bookingKey()
                    },
                    body: new URLSearchParams({
                        comboItems: comboCsv,
                        promotionCode: promo,
                        paymentMethod: selectedPaymentMethod
                    })
                });

                const payData = await payRes.json();
                if (payData.error) {
                    sideErrorMessage.textContent = payData.message;
                    btnContinue.disabled = false;
                    btnContinue.textContent = originalText;
                    return;
                }

                // Success! Fill step 5 details
                forgetOrder();
                document.getElementById('resTicketCode').textContent = payData.ticketCode;
                document.getElementById('resFilmTitle').textContent = sideFilmTitle.textContent;
                document.getElementById('resCinemaName').textContent = document.querySelector('#sideCinemaInfo strong').textContent;
                document.getElementById('resShowtime').textContent = document.querySelector('#sideCinemaInfo div').textContent.replace('📅', '').trim();
                document.getElementById('resSeats').textContent = selectedSeats.map(s => s.seatKey).join(', ');
                
                const comboNames = Array.from(document.querySelectorAll('.combo-input-new'))
                    .filter(input => Number(input.value) > 0)
                    .map(input => `${input.value}x ${input.dataset.name}`)
                    .join(', ');
                document.getElementById('resCombos').textContent = comboNames || 'Không có';
                document.getElementById('resTotal').textContent = sideTotalPrice.textContent;
                
                // Set QR code source
                document.getElementById('resQrCode').src = contextPath + '/tickets/qr/' + payData.ticketCode;

                setStep(5);
            } catch (err) {
                sideErrorMessage.textContent = 'Gặp lỗi trong quá trình xử lý đơn hàng.';
                btnContinue.disabled = false;
                btnContinue.textContent = originalText;
            }
        } else if (currentStep === 5) {
            window.location.href = contextPath + '/orders/history';
        }
    });

    btnBack.addEventListener('click', () => {
        if (currentStep > 1) {
            setStep(currentStep - 1);
        }
    });

    // Helper functions
    function toggleAccordion(id) {
        const item = document.getElementById(id);
        if (item) {
            item.classList.toggle('closed');
        }
    }
    window.toggleAccordion = toggleAccordion;

    function money(value) {
        return new Intl.NumberFormat('vi-VN').format(Math.max(0, Number(value || 0))) + ' \u0111';
    }

    function formatVietnameseDate(dateStr) {
        const parts = dateStr.split('-');
        if (parts.length !== 3) return dateStr;
        return parts[2] + "/" + parts[1] + "/" + parts[0];
    }
})();
