package ai.Mayi.service;

import ai.Mayi.apiPayload.code.status.ErrorStatus;
import ai.Mayi.apiPayload.exception.handler.UserHandler;
import ai.Mayi.domain.User;
import ai.Mayi.jwt.CookieUtil;
import ai.Mayi.jwt.JwtUtil;
import ai.Mayi.repository.UserRepository;
import ai.Mayi.web.dto.JwtTokenDTO;
import ai.Mayi.web.dto.UserDTO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    public void signUp(UserDTO.JoinRequestDTO joinDto) {

        var result = userRepository.findByUserEmail(joinDto.getUserEmail());
        if (result.isPresent()) {
            throw new UserHandler(ErrorStatus._SAME_EMAIL);
        }

        String email = joinDto.getUserEmail();
        String username = joinDto.getUserName();
        String password = joinDto.getUserPassword();
        String role = "USER";
        var hash_password = new BCryptPasswordEncoder().encode(password);

        User user = User.builder()
                .userEmail(email)
                .userName(username)
                .userPassword(hash_password)
                .roles(List.of(role))
                .build();

        userRepository.save(user);
    }

    public User findUserById(Long userId){
        return userRepository.findByUserId(userId).orElseThrow(() -> new UserHandler(ErrorStatus._NOT_EXIST_USER));
    }

    @Transactional
    public void commonLogin(UserDTO.LoginRequestDTO loginRequestDTO, HttpServletResponse response) {

        String userEmail = loginRequestDTO.getUserEmail();
        String userPassword = loginRequestDTO.getUserPassword();

        Optional<User> userOpt = Optional.ofNullable(userRepository.findByUserEmail(userEmail).orElseThrow(() -> new UserHandler(ErrorStatus._NOT_EXIST_EMAIL)));

        User user = userOpt.orElseThrow(() -> new UserHandler(ErrorStatus._NOT_EXIST_USER));

        if (!passwordEncoder.matches(userPassword, user.getUserPassword())) {
            log.error("비밀번호가 일치하지 않습니다.");
            throw new UserHandler(ErrorStatus._NOT_MATCH_PASSWORD);
        }

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userEmail, userPassword);

        Authentication authentication = authenticationManagerBuilder
                .getObject().authenticate(authenticationToken);

        JwtTokenDTO jwtTokenDTO = JwtUtil.generateToken(authentication);

        //login, refreshToken save
        user.updateRefreshToken(jwtTokenDTO.getRefreshToken());
        userRepository.save(user);

        sendTokenResponse(response, jwtTokenDTO);
    }

    public void loginOut(HttpServletResponse response) {
        CookieUtil.addCookie(response, "accessToken", null, 0);
        CookieUtil.addCookie(response, "refreshToken", null, 0);
    }


    public void sendTokenResponse(HttpServletResponse response, JwtTokenDTO jwtTokenDTO){
        // Cookie AccessToken lifeTime : 1m
        CookieUtil.addCookie(response, "accessToken", jwtTokenDTO.getAccessToken(), 1800);
        // Cookie RefreshToken lifeTime : 1h
        CookieUtil.addCookie(response, "refreshToken", jwtTokenDTO.getRefreshToken(), 3600);
    }
}
