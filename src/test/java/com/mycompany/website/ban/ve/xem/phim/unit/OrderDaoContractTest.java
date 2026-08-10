package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-007 — khong duoc co lai truy van doc toan bo bang Orders khong phan trang.
 *
 * <p>{@code findAllOrdersForAdmin()} tung ton tai o {@code OrderDAO}: doc toan bo {@code Orders}
 * roi ban them 2 query cho tung don. No khong con caller nao nen da bi xoa. Test nay chan viec
 * ai do them lai mot ham tra {@code List} khong co tham so phan trang.</p>
 */
class OrderDaoContractTest {

    @Test
    @DisplayName("OrderDAO khong con ham doc toan bo don khong phan trang")
    void orderDaoHasNoUnboundedAdminOrderQuery() {
        List<String> forbidden = Arrays.stream(OrderDAO.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.equals("findAllOrdersForAdmin") || name.equals("findAllOrders"))
                .toList();

        assertTrue(forbidden.isEmpty(),
                "OrderDAO khong duoc khai bao truy van don khong phan trang: " + forbidden);
    }
}
