package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AboutUsServlet extends BasePortalServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("members", CustomContentHelper.getAboutUsMembers());
        request.setAttribute("features", CustomContentHelper.getAboutUsFeatures());
        forward(request, response, "/WEB-INF/views/about-us.jsp");
    }
}
