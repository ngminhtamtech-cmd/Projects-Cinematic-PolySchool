package com.mycompany.website.ban.ve.xem.phim.service.payment;

import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import java.util.Map;

public interface PaymentGateway {
    PaymentResult createPayment(OrderRecord order);
    PaymentResult verifyCallback(Map<String, String> params);
    String getProviderName();
}
