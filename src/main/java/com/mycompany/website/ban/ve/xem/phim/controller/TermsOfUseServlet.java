package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.service.PolicyDocumentService;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TermsOfUseServlet extends BasePortalServlet {
    private final PolicyDocumentService policyService = new PolicyDocumentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            request.setAttribute("policy", policyService.publishedTermsOfUse());
            forward(request, response, "/WEB-INF/views/terms-of-use.jsp");
        } catch (RuntimeException ex) {
            throw new ServletException("Terms of use policy is unavailable", ex);
        }
    }
}
