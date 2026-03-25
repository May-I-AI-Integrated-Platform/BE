package ai.Mayi.service;

import ai.Mayi.apiPayload.code.status.ErrorStatus;
import ai.Mayi.apiPayload.exception.handler.TokenHandler;
import ai.Mayi.apiPayload.exception.handler.MessageHandler;
import ai.Mayi.converter.MessageConverter;
import ai.Mayi.domain.*;
import ai.Mayi.domain.enums.MessageType;
import ai.Mayi.domain.enums.TokenType;
import ai.Mayi.repository.ChatRepository;
import ai.Mayi.repository.MessageRepository;
import ai.Mayi.repository.ModelMessageRepository;
import ai.Mayi.repository.TokenRepository;
import ai.Mayi.web.dto.MessageDTO;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final TokenRepository tokenRepository;
    private final MessageConverter messageConverter;
    private final ModelMessageRepository modelMessageRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${ai.model.gpt}") private String gptModel;
    @Value("${ai.api.url.gpt}") private String gptUrl;
    @Value("${ai.model.claude}") private String claudeModel;
    @Value("${ai.api.url.claude}") private String claudeUrl;

    // API 요청 타임아웃 (초)
    private static final int API_TIMEOUT_SECONDS = 30;

    public Message enterChat(Chat chat, String text){
        Message message = Message.builder()
                .chat(chat)
                .messageAt(LocalDateTime.now())
                .text(text)
                .build();

        return messageRepository.save(message);
    }

    public Mono<MessageDTO.ChatResDTO> GPTService(Message userMessage, Token token) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("gptCircuitBreaker");

        User user = userMessage.getChat().getUser();

        if (token == null) {
            log.warn("[GPT] 사용자 토큰이 없습니다. userId={}", user.getUserId());
            return Mono.empty();
        }

        MessageDTO.gptReqDTO chatRequest = new MessageDTO.gptReqDTO(gptModel, userMessage.getText());

        WebClient webClient = WebClient.builder()
                .baseUrl(gptUrl)
                .defaultHeader("Authorization", "Bearer " + token.getTokenValue())
                .build();

        return webClient.post()
                .uri(gptUrl)
                .bodyValue(chatRequest)
                .retrieve()
                .bodyToMono(MessageDTO.gptResDTO.class)
                .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                .flatMap(response -> Mono.fromCallable(() -> {
                    String text = response.getChoices().get(0).getMessage().getContent();
                    modelMessageRepository.save(ModelMessage.builder()
                            .message(userMessage).messageType(MessageType.GPT)
                            .messageAt(LocalDateTime.now()).text(text).build());
                    return MessageDTO.ChatResDTO.builder().text(text).messageType(MessageType.GPT).build();
                }).subscribeOn(Schedulers.boundedElastic()))
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(CallNotPermittedException.class, e -> {
                    log.warn("[GPT] Circuit Breaker OPEN 상태. state={}", circuitBreaker.getState());
                    return Mono.just(MessageDTO.ChatResDTO.builder()
                            .text("[GPT 서비스 일시 불가] 잠시 후 다시 시도해주세요.")
                            .messageType(MessageType.GPT).isError(true).build());
                })
                .onErrorResume(e -> {
                    log.error("[GPT] API 호출 실패: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<MessageDTO.ChatResDTO> DeepSeekService(Message userMessage, Token token) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("deepseekCircuitBreaker");

        if (token == null) {
            log.warn("[DeepSeek] 사용자 토큰이 없습니다.");
            return Mono.empty();
        }
        String key = token.getTokenValue();

        List<MessageDTO.DeepSeekMessage> messageList = new ArrayList<>();
        messageList.add(MessageDTO.DeepSeekMessage.builder().role("user").content(userMessage.getText()).build());
        MessageDTO.DeepSeekChatReqDTO reqBody = MessageDTO.DeepSeekChatReqDTO.builder()
                .model("deepseek-chat").messages(messageList).build();

        return WebClient.builder().build().post()
                .uri("https://api.deepseek.com/chat/completions")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + key)
                .bodyValue(reqBody)
                .retrieve()
                .bodyToMono(MessageDTO.DeepSeekChatResDTO.class)
                .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                .flatMap(resBody -> Mono.fromCallable(() -> {
                    String text = resBody.getChoices().get(0).getMessage().getContent();
                    modelMessageRepository.save(ModelMessage.builder()
                            .message(userMessage).messageType(MessageType.DEEPSEEK)
                            .messageAt(LocalDateTime.now()).text(text).build());
                    return MessageDTO.ChatResDTO.builder().text(text).messageType(MessageType.DEEPSEEK).build();
                }).subscribeOn(Schedulers.boundedElastic()))
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(CallNotPermittedException.class, e -> {
                    log.warn("[DeepSeek] Circuit Breaker OPEN 상태. state={}", circuitBreaker.getState());
                    return Mono.just(MessageDTO.ChatResDTO.builder()
                            .text("[DeepSeek 서비스 일시 불가] 잠시 후 다시 시도해주세요.")
                            .messageType(MessageType.DEEPSEEK).isError(true).build());
                })
                .onErrorResume(e -> {
                    log.error("[DeepSeek] API 호출 실패: {}", e.getMessage());
                    return Mono.empty();
                });
    }




    public Mono<MessageDTO.ChatResDTO> BardService(Message userMessage, Token token) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("bardCircuitBreaker");

        if (token == null) {
            log.warn("[Bard] 사용자 토큰이 없습니다.");
            return Mono.empty();
        }
        String key = token.getTokenValue();
        String uri = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + key;

        List<MessageDTO.BardContents> contentsList = new ArrayList<>();
        contentsList.add(messageConverter.toBardContents(userMessage));
        MessageDTO.BardChatReqDTO reqBody = MessageDTO.BardChatReqDTO.builder().contents(contentsList).build();

        return WebClient.builder().build().post()
                .uri(uri)
                .bodyValue(reqBody)
                .retrieve()
                .bodyToMono(MessageDTO.BardChatResDTO.class)
                .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                .flatMap(resBody -> Mono.fromCallable(() -> {
                    String text = resBody.getCandidates().get(0).getContent().getParts().get(0).getText();
                    modelMessageRepository.save(ModelMessage.builder()
                            .message(userMessage).messageType(MessageType.BARD)
                            .messageAt(LocalDateTime.now()).text(text).build());
                    return MessageDTO.ChatResDTO.builder().text(text).messageType(MessageType.BARD).build();
                }).subscribeOn(Schedulers.boundedElastic()))
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(CallNotPermittedException.class, e -> {
                    log.warn("[Bard] Circuit Breaker OPEN 상태. state={}", circuitBreaker.getState());
                    return Mono.just(MessageDTO.ChatResDTO.builder()
                            .text("[Bard 서비스 일시 불가] 잠시 후 다시 시도해주세요.")
                            .messageType(MessageType.BARD).isError(true).build());
                })
                .onErrorResume(e -> {
                    log.error("[Bard] API 호출 실패: {}", e.getMessage());
                    return Mono.empty();
                });
    }


    public Mono<MessageDTO.ChatResDTO> ClaudeService(Message userMessage, Token token) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("claudeCircuitBreaker");

        User user = userMessage.getChat().getUser();

        if (token == null) {
            log.warn("[Claude] 사용자 토큰이 없습니다. userId={}", user.getUserId());
            return Mono.empty();
        }

        MessageDTO.ClaudeReqDto request = MessageDTO.ClaudeReqDto.builder()
                .model(claudeModel)
                .messages(List.of(MessageDTO.ClaudeReqDto.Message.builder()
                        .role("user").content(userMessage.getText()).build()))
                .max_tokens(1000)
                .build();

        return WebClient.builder()
                .baseUrl(claudeUrl)
                .defaultHeader("x-api-key", token.getTokenValue())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build().post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MessageDTO.claudeResDto.class)
                .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                .flatMap(response -> Mono.fromCallable(() -> {
                    String text = response.getContent().get(0).getText();
                    modelMessageRepository.save(ModelMessage.builder()
                            .message(userMessage).messageType(MessageType.CLAUDE)
                            .messageAt(LocalDateTime.now()).text(text).build());
                    return MessageDTO.ChatResDTO.builder().text(text).messageType(MessageType.CLAUDE).build();
                }).subscribeOn(Schedulers.boundedElastic()))
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(CallNotPermittedException.class, e -> {
                    log.warn("[Claude] Circuit Breaker OPEN 상태. state={}", circuitBreaker.getState());
                    return Mono.just(MessageDTO.ChatResDTO.builder()
                            .text("[Claude 서비스 일시 불가] 잠시 후 다시 시도해주세요.")
                            .messageType(MessageType.CLAUDE).isError(true).build());
                })
                .onErrorResume(e -> {
                    log.error("[Claude] API 호출 실패: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    public MessageDTO.getChatResDTO getMessageList(User user, Chat chat){
        if(chat.getUser() != user){
            throw new MessageHandler(ErrorStatus._NOT_MATCH_CHAT);
        }

        List<MessageDTO.messageDTO> messageDto = new ArrayList<>();

        messageRepository.findByChat(chat).forEach(message -> {
            //user Message 추가
            List<MessageDTO.ChatResDTO> messageUserDTO = new ArrayList<>();
            messageUserDTO.add(MessageDTO.ChatResDTO.builder()
                    .messageType(MessageType.USER)
                    .text(message.getText())
                    .build());
            messageDto.add(MessageDTO.messageDTO.builder()
                    .isUser(true)
                    .messages(messageUserDTO)
                    .build());

            List<MessageDTO.ChatResDTO> messageModelDTO = new ArrayList<>();
            messageModelDTO = message.getModelMessages().stream()
                    .map(modelMessage -> MessageDTO.ChatResDTO.builder()
                            .messageType(modelMessage.getMessageType())
                            .text(modelMessage.getText())
                            .build())
                    .toList();
            messageDto.add(MessageDTO.messageDTO.builder()
                    .isUser(false)
                    .messages(messageModelDTO)
                    .build());
        });

        return MessageDTO.getChatResDTO.builder()
                .chatId(chat.getChatId())
                .chatName(chat.getChatName())
                .messages(messageDto)
                .build();
    }
}