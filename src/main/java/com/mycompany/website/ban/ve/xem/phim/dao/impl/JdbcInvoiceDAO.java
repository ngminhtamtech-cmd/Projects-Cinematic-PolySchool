package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.InvoiceDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Invoice;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcInvoiceDAO implements InvoiceDAO {
    @Override
    public Optional<Invoice> findSaleByOrderId(int orderId) {
        String sql = "SELECT * FROM Invoices WHERE OrderId=? AND InvoiceType='sale'";
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot load invoice", ex);
        }
    }

    @Override
    public List<Invoice> findByOrderId(int orderId) {
        String sql = "SELECT * FROM Invoices WHERE OrderId=? ORDER BY IssuedAt";
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            List<Invoice> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
            return result;
        } catch (SQLException ex) {
            throw new DaoException("Cannot load invoices", ex);
        }
    }

    @Override
    public int insert(Connection c, Invoice invoice) {
        String sql = """
                INSERT INTO Invoices(OrderId, InvoiceNo, InvoiceType, OriginalInvoiceId, TaxCode,
                  BuyerName, BuyerTaxCode, SubTotal, VatRate, VatAmount, Total)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, invoice.getOrderId());
            ps.setString(2, invoice.getInvoiceNo());
            ps.setString(3, invoice.getInvoiceType());
            if (invoice.getOriginalInvoiceId() == null) ps.setNull(4, java.sql.Types.INTEGER);
            else ps.setInt(4, invoice.getOriginalInvoiceId());
            ps.setString(5, invoice.getTaxCode());
            ps.setString(6, invoice.getBuyerName());
            ps.setString(7, invoice.getBuyerTaxCode());
            ps.setBigDecimal(8, invoice.getSubTotal());
            ps.setBigDecimal(9, invoice.getVatRate());
            ps.setBigDecimal(10, invoice.getVatAmount());
            ps.setBigDecimal(11, invoice.getTotal());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
            throw new DaoException("Invoice id was not returned", null);
        } catch (SQLException ex) {
            throw new DaoException("Cannot insert invoice", ex);
        }
    }

    @Override
    public void updatePdfPath(int invoiceId, String path) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE Invoices SET PdfPath=? WHERE Id=?")) {
            ps.setString(1, path);
            ps.setInt(2, invoiceId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException("Cannot save invoice PDF path", ex);
        }
    }

    private Invoice map(ResultSet rs) throws SQLException {
        Invoice i = new Invoice();
        i.setId(rs.getInt("Id"));
        i.setOrderId(rs.getInt("OrderId"));
        i.setInvoiceNo(rs.getString("InvoiceNo"));
        i.setInvoiceType(rs.getString("InvoiceType"));
        int originalId = rs.getInt("OriginalInvoiceId");
        i.setOriginalInvoiceId(rs.wasNull() ? null : originalId);
        i.setIssuedAt(rs.getTimestamp("IssuedAt").toLocalDateTime());
        i.setTaxCode(rs.getString("TaxCode"));
        i.setBuyerName(rs.getString("BuyerName"));
        i.setBuyerTaxCode(rs.getString("BuyerTaxCode"));
        i.setSubTotal(rs.getBigDecimal("SubTotal"));
        i.setVatRate(rs.getBigDecimal("VatRate"));
        i.setVatAmount(rs.getBigDecimal("VatAmount"));
        i.setTotal(rs.getBigDecimal("Total"));
        i.setPdfPath(rs.getString("PdfPath"));
        return i;
    }
}
