package ai.Mayi.service;

import ai.Mayi.apiPayload.code.status.ErrorStatus;
import ai.Mayi.apiPayload.exception.handler.TokenHandler;
import ai.Mayi.domain.Token;
import ai.Mayi.domain.User;
import ai.Mayi.domain.enums.TokenType;
import ai.Mayi.repository.TokenRepository;
import ai.Mayi.repository.UserRepository;
import ai.Mayi.web.dto.CommonDTO;
import ai.Mayi.web.dto.TokenDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;

    public CommonDTO.IsSuccessDTO saveToken(User user, TokenDTO.saveTokenReqDto request){
        final List<Token> tokenList = tokenRepository.findByUser(user);

        //입력한 토큰 정보 삽입
        request.getTokenList().forEach(tokenDto -> {
            //토큰 타입 매칭
            TokenType tokenType;
            if(tokenDto.getTokenType().toUpperCase().equals(TokenType.GPT.toString())){
                tokenType = TokenType.GPT;
            } else if (tokenDto.getTokenType().toUpperCase().equals(TokenType.DEEPSEEK.toString())) {
                tokenType = TokenType.DEEPSEEK;
            } else if (tokenDto.getTokenType().toUpperCase().equals(TokenType.BARD.toString())) {
                tokenType = TokenType.BARD;
            } else if (tokenDto.getTokenType().toUpperCase().equals(TokenType.CLAUDE.toString())) {
                tokenType = TokenType.CLAUDE;
            } else {
                throw new TokenHandler(ErrorStatus._NOT_EXIST_TOKEN_TYPE);
            }

            //기존 토큰 정보 삭제
            Optional<Token> oldToken = tokenList.stream().filter(token -> token.getTokenType().equals(tokenType)).findFirst();
            oldToken.ifPresent(tokenRepository::delete);

            //토큰 저장
            tokenRepository.save(Token.builder().user(user).tokenType(tokenType).tokenValue(tokenDto.getValue()).build());
        });

        return CommonDTO.IsSuccessDTO.builder()
                .isSuccess(true)
                .build();
    }

    public TokenDTO.getTokenResDto getTokenList(User user){
        List<TokenDTO.tokenDto> tokenDtoList = tokenRepository.findByUser(user).stream()
                .map(token -> TokenDTO.tokenDto.builder().
                        tokenType(token.getTokenType().toString())
                        .value(token.getTokenValue())
                        .build()
                )
                .toList();
        return TokenDTO.getTokenResDto.builder()
                .userId(user.getUserId())
                .tokenList(tokenDtoList)
                .build();
    }

    @Cacheable(value = "token", key = "#user.userId + #tokenType.name()")
    public Token getToken(User user, TokenType tokenType){
        return tokenRepository.findByUserAndTokenType(user, tokenType);
    }
}
