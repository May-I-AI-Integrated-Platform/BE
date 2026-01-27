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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
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

    @Async
    public CompletableFuture<MessageDTO.ChatResDTO> GPTService(Message userMessage) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("gptCircuitBreaker");

        User user = userMessage.getChat().getUser();
        Token token = tokenRepository.findByUserAndTokenType(user, TokenType.GPT);

        if (token == null) {
            log.warn("[GPT] 사용자 토큰이 없습니다. userId={}", user.getUserId());
            return CompletableFuture.completedFuture(null);
        }

        MessageDTO.gptReqDTO chatRequest = new MessageDTO.gptReqDTO(gptModel, userMessage.getText());

        // Circuit Breaker로 API 호출 감싸기
        Supplier<MessageDTO.ChatResDTO> apiCall = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            WebClient webClient = WebClient.builder()
                    .baseUrl(gptUrl)
                    .defaultHeader("Authorization", "Bearer " + token.getTokenValue())
                    .build();

            MessageDTO.gptResDTO response = webClient.post()
                    .uri(gptUrl)
                    .bodyValue(chatRequest)
                    .retrieve()
                    .bodyToMono(MessageDTO.gptResDTO.class)
                    .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                    .block();

            if (response != null) {
                String text = response.getChoices().get(0).getMessage().getContent();

                ModelMessage message = ModelMessage.builder()
                        .message(userMessage)
                        .messageType(MessageType.GPT)
                        .messageAt(LocalDateTime.now())
                        .text(text)
                        .build();
                modelMessageRepository.save(message);

                return MessageDTO.ChatResDTO.builder()
                        .text(text)
                        .messageType(MessageType.GPT)
                        .build();
            }
            return null;
        });

        try {
            MessageDTO.ChatResDTO result = apiCall.get();
            return CompletableFuture.completedFuture(result);
        } catch (CallNotPermittedException e) {
            log.warn("[GPT] Circuit Breaker OPEN 상태 - 요청 차단됨. state={}", circuitBreaker.getState());
            return CompletableFuture.completedFuture(
                    MessageDTO.ChatResDTO.builder()
                            .text("[GPT 서비스 일시 불가] 잠시 후 다시 시도해주세요.")
                            .messageType(MessageType.GPT)
                            .isError(true)
                            .build());
        } catch (Exception e) {
            log.error("[GPT] API 호출 실패: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async
    public CompletableFuture<MessageDTO.ChatResDTO> DeepSeekService(Message userMessage) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("deepseekCircuitBreaker");

        // get User Token
        Token deepSeekToken = tokenRepository.findByUser(userMessage.getChat().getUser()).stream()
                .filter(token -> token.getTokenType().toString().equals(MessageType.DEEPSEEK.toString()))
                .findFirst()
                .orElse(null);

        if (deepSeekToken == null) {
            log.warn("[DeepSeek] 사용자 토큰이 없습니다.");
            return CompletableFuture.completedFuture(null);
        }
        String key = deepSeekToken.getTokenValue();

        // Circuit Breaker로 API 호출 감싸기
        Supplier<MessageDTO.ChatResDTO> apiCall = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            WebClient webClient = WebClient.builder().build();
            String uri = "https://api.deepseek.com/chat/completions";

            List<MessageDTO.DeepSeekMessage> messageList = new ArrayList<>();
            messageList.add(MessageDTO.DeepSeekMessage.builder().role("user").content(userMessage.getText()).build());
            MessageDTO.DeepSeekChatReqDTO reqBody = MessageDTO.DeepSeekChatReqDTO.builder()
                    .model("deepseek-chat")
                    .messages(messageList)
                    .build();

            MessageDTO.DeepSeekChatResDTO resBody = webClient.post()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .body(Mono.just(reqBody), MessageDTO.DeepSeekChatReqDTO.class)
                    .retrieve()
                    .bodyToMono(MessageDTO.DeepSeekChatResDTO.class)
                    .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                    .block();

            if (resBody != null) {
                String text = resBody.getChoices().get(0).getMessage().getContent();

                ModelMessage message = ModelMessage.builder()
                        .message(userMessage)
                        .messageType(MessageType.DEEPSEEK)
                        .messageAt(LocalDateTime.now())
                        .text(text)
                        .build();
                modelMessageRepository.save(message);

                return MessageDTO.ChatResDTO.builder()
                        .text(text)
                        .messageType(MessageType.DEEPSEEK)
                        .build();
            }
            return null;
        });

        try {
            MessageDTO.ChatResDTO result = apiCall.get();
            return CompletableFuture.completedFuture(result);
        } catch (CallNotPermittedException e) {
            log.warn("[DeepSeek] Circuit Breaker OPEN 상태 - 요청 차단됨. state={}", circuitBreaker.getState());
            return CompletableFuture.completedFuture(
                    MessageDTO.ChatResDTO.builder()
                            .text("[DeepSeek 서비스 일시 불가] 잠시 후 다시 시도해주세요.")
                            .messageType(MessageType.DEEPSEEK)
                            .isError(true)
                            .build());
        } catch (Exception e) {
            log.error("[DeepSeek] API 호출 실패: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }




    @Async
    public CompletableFuture<MessageDTO.ChatResDTO> BardService(Message userMessage) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("bardCircuitBreaker");

        // get User Token
        Token bardToken = tokenRepository.findByUser(userMessage.getChat().getUser()).stream()
                .filter(token -> token.getTokenType().toString().equals(MessageType.BARD.toString()))
                .findFirst()
                .orElse(null);

        if (bardToken == null) {
            log.warn("[Bard] 사용자 토큰이 없습니다.");
            return CompletableFuture.completedFuture(null);
        }
        String key = bardToken.getTokenValue();

        // Circuit Breaker로 API 호출 감싸기
        Supplier<MessageDTO.ChatResDTO> apiCall = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            WebClient webClient = WebClient.builder().build();
            String uri = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + key;

            List<MessageDTO.BardContents> contentsList = new ArrayList<>();
            contentsList.add(messageConverter.toBardContents(userMessage));

            MessageDTO.BardChatReqDTO reqBody = MessageDTO.BardChatReqDTO.builder()
                    .contents(contentsList)
                    .build();

            MessageDTO.BardChatResDTO resBody = webClient.post()
                    .uri(uri)
                    .body(Mono.just(reqBody), MessageDTO.BardChatReqDTO.class)
                    .retrieve()
                    .bodyToMono(MessageDTO.BardChatResDTO.class)
                    .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                    .block();

            if (resBody != null) {
                String text = resBody.getCandidates().get(0).getContent().getParts().get(0).getText();

                ModelMessage message = ModelMessage.builder()
                        .message(userMessage)
                        .messageType(MessageType.BARD)
                        .messageAt(LocalDateTime.now())
                        .text(text)
                        .build();
                modelMessageRepository.save(message);

                return MessageDTO.ChatResDTO.builder()
                        .text(text)
                        .messageType(MessageType.BARD)
                        .build();
            }
            return null;
        });

        try {
            MessageDTO.ChatResDTO result = apiCall.get();
            return CompletableFuture.completedFuture(result);
        } catch (CallNotPermittedException e) {
            log.warn("[Bard] Circuit Breaker OPEN 상태 - 요청 차단됨. state={}", circuitBreaker.getState());
            return CompletableFuture.completedFuture(
                    MessageDTO.ChatResDTO.builder()
                            .text("[Bard 서비스 일시 불가] 잠시 후 다시 시도해주세요.")
                            .messageType(MessageType.BARD)
                            .isError(true)
                            .build());
        } catch (Exception e) {
            log.error("[Bard] API 호출 실패: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }


    @Async
    public CompletableFuture<MessageDTO.ChatResDTO> ClaudeService(Message userMessage) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("claudeCircuitBreaker");

        User user = userMessage.getChat().getUser();
        Token token = tokenRepository.findByUserAndTokenType(user, TokenType.CLAUDE);
        int maxTokens = 1000;

        if (token == null) {
            log.warn("[Claude] 사용자 토큰이 없습니다. userId={}", user.getUserId());
            return CompletableFuture.completedFuture(null);
        }

        // Circuit Breaker로 API 호출 감싸기
        Supplier<MessageDTO.ChatResDTO> apiCall = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            WebClient webClient = WebClient.builder()
                    .baseUrl(claudeUrl)
                    .defaultHeader("x-api-key", token.getTokenValue())
                    .defaultHeader("anthropic-version", "2023-06-01")
                    .defaultHeader("content-type", "application/json")
                    .build();

            MessageDTO.ClaudeReqDto request = MessageDTO.ClaudeReqDto.builder()
                    .model(claudeModel)
                    .messages(List.of(MessageDTO.ClaudeReqDto.Message.builder()
                            .role("user")
                            .content(userMessage.getText())
                            .build()))
                    .max_tokens(maxTokens)
                    .build();

            MessageDTO.claudeResDto response = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MessageDTO.claudeResDto.class)
                    .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                    .block();

            if (response != null) {
                String text = response.getContent().get(0).getText();

                ModelMessage message = ModelMessage.builder()
                        .message(userMessage)
                        .messageType(MessageType.CLAUDE)
                        .messageAt(LocalDateTime.now())
                        .text(text)
                        .build();
                modelMessageRepository.save(message);

                return MessageDTO.ChatResDTO.builder()
                        .text(text)
                        .messageType(MessageType.CLAUDE)
                        .build();
            }
            return null;
        });

        try {
            MessageDTO.ChatResDTO result = apiCall.get();
            return CompletableFuture.completedFuture(result);
        } catch (CallNotPermittedException e) {
            log.warn("[Claude] Circuit Breaker OPEN 상태 - 요청 차단됨. state={}", circuitBreaker.getState());
            return CompletableFuture.completedFuture(
                    MessageDTO.ChatResDTO.builder()
                            .text("[Claude 서비스 일시 불가] 잠시 후 다시 시도해주세요.")
                            .messageType(MessageType.CLAUDE)
                            .isError(true)
                            .build());
        } catch (Exception e) {
            log.error("[Claude] API 호출 실패: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
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