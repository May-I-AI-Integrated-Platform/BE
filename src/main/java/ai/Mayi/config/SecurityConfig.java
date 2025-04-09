package ai.Mayi.config;

import ai.Mayi.jwt.JwtAuthenticationFilter;
import ai.Mayi.jwt.JwtUtil;
import ai.Mayi.oauth.CustomOAuth2SuccessHandler;
import ai.Mayi.oauth.CustomOAuth2UserService;
import ai.Mayi.oauth.OAuth2RegistrationIdFilter;
import ai.Mayi.repository.UserRepository;
import ai.Mayi.service.MyUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final MyUserDetailsService myUserDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOAuth2SuccessHandler customOAuth2SuccessHandler;
    private final OAuth2RegistrationIdFilter oAuth2RegistrationIdFilter;
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, userRepository, myUserDetailsService);
    }

    //비밀번호 해싱하는 코드
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST API -> CSRF 보호 비활성화
                .httpBasic(httpBasic -> httpBasic.disable())
                .csrf(csrf -> csrf.disable())
                // session disable
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 요청에 대한 인증 및 권한 설정
                .authorizeHttpRequests(auth -> auth
//                       .requestMatchers("/**").permitAll() //
                                .requestMatchers("/user/test").hasRole("USER") // "USER" 권한테스트
                                .anyRequest().permitAll() // 인증 x
                )
                // oauth
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                // login
                                .userService(customOAuth2UserService)
                        )
                        // After login
                        .successHandler(customOAuth2SuccessHandler)
                )
                .addFilterBefore(oAuth2RegistrationIdFilter, OAuth2AuthorizationRequestRedirectFilter.class)
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}