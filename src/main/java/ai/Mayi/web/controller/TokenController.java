package ai.Mayi.web.controller;

import ai.Mayi.apiPayload.ApiResponse;
import ai.Mayi.domain.User;
import ai.Mayi.jwt.CookieUtil;
import ai.Mayi.service.TokenService;
import ai.Mayi.service.UserServiceImpl;
import ai.Mayi.web.dto.CommonDTO;
import ai.Mayi.web.dto.TokenDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "TokenController", description = "토큰 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/token")
public class TokenController {
    private final TokenService tokenService;
    private final UserServiceImpl userService;

    @PostMapping("")
    @Operation(summary = "토큰 저장 API")
    public ApiResponse<CommonDTO.IsSuccessDTO> saveToken(HttpServletRequest http, @RequestBody @Valid TokenDTO.saveTokenReqDto request) {
        String accessToken = CookieUtil.getCookieValue(http, "accessToken");
        User user = userService.findByAccessToken(accessToken);

        return ApiResponse.onSuccess(tokenService.saveToken(user, request));
    }

    @GetMapping("")
    @Operation(summary = "토큰 조회 API")
    public ApiResponse<TokenDTO.getTokenResDto> getToken(HttpServletRequest http) {
        String accessToken = CookieUtil.getCookieValue(http, "accessToken");
        User user = userService.findByAccessToken(accessToken);

        return ApiResponse.onSuccess(tokenService.getTokenList(user));
    }
}
