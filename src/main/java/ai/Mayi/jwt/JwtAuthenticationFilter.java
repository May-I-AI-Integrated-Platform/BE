package ai.Mayi.jwt;

import ai.Mayi.apiPayload.code.status.ErrorStatus;
import ai.Mayi.domain.User;
import ai.Mayi.repository.UserRepository;
import ai.Mayi.service.MyUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final MyUserDetailsService myUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain
    ) throws ServletException, IOException {

        String accessToken = CookieUtil.getCookieValue(request, "accessToken");

        // accessToken 검사
        if (accessToken != null && jwtUtil.validateToken(accessToken)) {
            Authentication authentication = jwtUtil.getAuthentication(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            //log.info("accessToken 인증 완료 유저 : {}", authentication.getPrincipal());
        } else {
            // RefreshToken 검사
            String refreshToken = CookieUtil.getCookieValue(request, "refreshToken");

            if (refreshToken != null && jwtUtil.validateToken(refreshToken)) {
                String userEmail = jwtUtil.getUserEmail(refreshToken);

                if (userRepository.findByUserEmail(userEmail).isPresent()) {
                    log.info("리프레쉬 토큰안의 정보를 통한 이메일이 존재한다");

                    Optional<User> userOpt = userRepository.findByUserEmail(userEmail);
                    User user = userOpt.get();

                    if (refreshToken.equals(user.getRefreshToken())) {

                        log.info("리프레쉬토큰이 있어서 accessToken을 새로 발급하여 쿠키에 저장");

                        UserDetails userDetails = myUserDetailsService.loadUserByUsername(userEmail);
                        Authentication authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, "", userDetails.getAuthorities()
                        );
                        String newAccessToken = jwtUtil.generateAccessToken(authentication);
                        sendTokenResponse(response, newAccessToken);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        sendStatusResponse(response, ErrorStatus._REFRESHED_ACCESS_TOKEN);
                        return;
                    }
                }
            }

            sendStatusResponse(response, ErrorStatus._EXPIRED_REFRESH_TOKEN);
            return;
        }


        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return !path.startsWith("/user/data");
    }

    private void sendTokenResponse(HttpServletResponse response, String accessToken) {
        CookieUtil.addCookie(response, "accessToken", accessToken, 60 * 10);
    }

    private void sendStatusResponse(HttpServletResponse response, ErrorStatus errorStatus) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(errorStatus.getHttpStatus().value());

        String jsonResponse = String.format(
                "{ \"code\": \"%s\", \"message\": \"%s\", \"isSuccess\": false }",
                errorStatus.getCode(), errorStatus.getMessage()
        );

        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
