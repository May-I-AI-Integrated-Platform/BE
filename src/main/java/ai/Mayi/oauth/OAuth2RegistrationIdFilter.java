package ai.Mayi.oauth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2RegistrationIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();

        if (uri.startsWith("/oauth2/authorization/")) {
            String registrationId = uri.substring("/oauth2/authorization/".length());
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("registrationId", registrationId);
        }

        chain.doFilter(request, response);
    }
}
