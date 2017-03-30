<% response.sendRedirect("https://${WSO2_HOST}:${WSO2_PORT}/${STATIC_FILE_DIR}/" + (request.getQueryString() == null ? "" : "?" + request.getQueryString())); %>
