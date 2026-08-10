package com.mycompany.website.ban.ve.xem.phim.service.payment;

import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import java.util.Map;
import java.util.UUID;

public class SimulatedGateway implements PaymentGateway {

    @Override
    public PaymentResult createPayment(OrderRecord order) {
        String transactionId = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return PaymentResult.success(transactionId, getProviderName());
    }

    @Override
    public PaymentResult verifyCallback(Map<String, String> params) {
        String txId = params.getOrDefault("transactionId", "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        return PaymentResult.success(txId, getProviderName());
    }

    @Override
    public String getProviderName() {
        return "simulated";
    }
}
