package com.mycompany.website.ban.ve.xem.phim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "Ve ban TB / Ngay" tren {@code /admin/reports}.
 *
 * <p>Do tren he thong that ngay 07/08/2026: thang 08 ban duoc 23 ve trong 31 ngay, nhung o KPI
 * hien dung so <b>0 ve</b> — trong khi cung trang do "Tong doanh thu" bao 2.407.000 d va bang
 * "Top phim ban chay" liet ke 12+10+7+4 ghe. Nguyen nhan la phep chia nguyen
 * {@code ticketsSold / days}: 23/31 = 0.</p>
 */
@DisplayName("Bao cao: ve trung binh moi ngay")
class ReportAveragePerDayTest {

    @Test
    @DisplayName("ban it ve hon so ngay trong thang van phai ra so khac 0")
    void fewerTicketsThanDaysDoesNotCollapseToZero() {
        // Chinh so lieu thang 08/2026 tren CineBookDB.
        assertEquals(new BigDecimal("0.7"), AdminService.averagePerDay(23, 31),
                "23 ve / 31 ngay phai la 0.7, phep chia nguyen cu tra 0");
    }

    @Test
    @DisplayName("lam tron nua len, giu dung 1 chu so thap phan")
    void roundsHalfUpToOneDecimal() {
        assertEquals(new BigDecimal("2.0"), AdminService.averagePerDay(62, 31));
        assertEquals(new BigDecimal("1.5"), AdminService.averagePerDay(45, 30));
        assertEquals(new BigDecimal("0.0"), AdminService.averagePerDay(1, 31));
    }

    @Test
    @DisplayName("khong co ngay nao thi tra 0, khong chia cho 0")
    void guardsAgainstZeroDays() {
        assertEquals(BigDecimal.ZERO, AdminService.averagePerDay(10, 0));
        assertEquals(BigDecimal.ZERO, AdminService.averagePerDay(10, -1));
    }
}
