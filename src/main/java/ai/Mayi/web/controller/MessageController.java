package ai.Mayi.web.controller;

import ai.Mayi.apiPayload.ApiResponse;
import ai.Mayi.apiPayload.code.status.ErrorStatus;
import ai.Mayi.apiPayload.exception.handler.MessageHandler;
import ai.Mayi.domain.Chat;
import ai.Mayi.domain.Message;
import ai.Mayi.domain.User;
import ai.Mayi.domain.enums.MessageType;
import ai.Mayi.domain.enums.TokenType;
import ai.Mayi.jwt.CookieUtil;
import ai.Mayi.service.ChatService;
import ai.Mayi.service.MessageService;
import ai.Mayi.service.TokenService;
import ai.Mayi.service.UserServiceImpl;
import ai.Mayi.web.dto.MessageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Tag(name = "MessageController", description = "채팅 메세지 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/message")
public class MessageController {
    private final MessageService messageService;
    private final UserServiceImpl userService;
    private final ChatService chatService;
    private final TokenService tokenService;

    @PostMapping("")
    @Operation(summary = "채팅 입력 API")
    public Mono<ApiResponse<MessageDTO.enterChatResDTO>> enterChat(HttpServletRequest http, @RequestBody @Valid MessageDTO.enterChatReqDTO request) {
        String accessToken = CookieUtil.getCookieValue(http, "accessToken");
        User user = userService.findByAccessToken(accessToken);
        Chat chat = chatService.findChatById(request.getChatId());

        if (chat.getUser() != user) {
            throw new MessageHandler(ErrorStatus._NOT_MATCH_CHAT);
        }
        if (request.getAiTypeList().contains(MessageType.USER)) {
            throw new MessageHandler(ErrorStatus._INVALID_AI_TYPE);
        }

        Message userMessage = messageService.enterChat(chat, request.getText());

        // 요청된 AI 서비스 Mono 목록 구성
        List<Mono<MessageDTO.ChatResDTO>> monos = new ArrayList<>();
        if (request.getAiTypeList().contains(MessageType.GPT)) {
            monos.add(messageService.GPTService(userMessage, tokenService.getToken(user, TokenType.GPT)));
        }
        if (request.getAiTypeList().contains(MessageType.DEEPSEEK)) {
            monos.add(messageService.DeepSeekService(userMessage, tokenService.getToken(user, TokenType.DEEPSEEK)));
        }
        if (request.getAiTypeList().contains(MessageType.CLAUDE)) {
            monos.add(messageService.ClaudeService(userMessage, tokenService.getToken(user, TokenType.CLAUDE)));
        }
        if (request.getAiTypeList().contains(MessageType.BARD)) {
            monos.add(messageService.BardService(userMessage, tokenService.getToken(user, TokenType.BARD)));
        }

        int totalCount = monos.size();

        // 모든 AI를 병렬로 실행, 각각 실패해도 나머지 결과 수집
        return Flux.merge(monos)
                .collectList()
                //.doOnNext(list -> log.info("[Controller] 총 {}개 AI 중 {}개 응답 성공", totalCount, list.size()))
                .map(responseDTOList -> ApiResponse.onSuccess(
                        MessageDTO.enterChatResDTO.builder()
                                .chatId(chat.getChatId())
                                .responseDTOList(responseDTOList)
                                .build()
                ));
    }

    @GetMapping("/{chatId}")
    @Operation(summary = "메세지 조회 API")
    public ApiResponse<MessageDTO.getChatResDTO> getMessageList(HttpServletRequest request, @PathVariable Long chatId) {
        String accessToken = CookieUtil.getCookieValue(request, "accessToken");
        User user = userService.findByAccessToken(accessToken);
        Chat chat = chatService.findChatById(chatId);

        return ApiResponse.onSuccess(messageService.getMessageList(user, chat));
    }
}
