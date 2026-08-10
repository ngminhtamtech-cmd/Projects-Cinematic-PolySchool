package com.mycompany.website.ban.ve.xem.phim.service;

/** Typed outcome for the two workflows displayed by the appeal dashboard. */
public record AppealResolutionResult(
        Kind kind,
        Integer orderId,
        String ticketCode) {

    public enum Kind {
        ACCOUNT_RESOLVED,
        REFUND_WORKFLOW_REDIRECT
    }

    public static AppealResolutionResult accountResolved() {
        return new AppealResolutionResult(Kind.ACCOUNT_RESOLVED, null, null);
    }

    public static AppealResolutionResult refundRedirect(int orderId, String ticketCode) {
        return new AppealResolutionResult(Kind.REFUND_WORKFLOW_REDIRECT, orderId, ticketCode);
    }

    public boolean requiresRefundWorkflow() {
        return kind == Kind.REFUND_WORKFLOW_REDIRECT;
    }
}
