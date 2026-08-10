package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.Invoice;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface InvoiceDAO {
    Optional<Invoice> findSaleByOrderId(int orderId);
    List<Invoice> findByOrderId(int orderId);
    int insert(Connection connection, Invoice invoice);
    void updatePdfPath(int invoiceId, String path);
}
