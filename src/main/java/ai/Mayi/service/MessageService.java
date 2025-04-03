package ai.Mayi.service;

import ai.Mayi.apiPayload.code.status.ErrorStatus;
import ai.Mayi.apiPayload.exception.handler.TokenHandler;
import ai.Mayi.config.AIConfig;
import ai.Mayi.domain.Chat;
import ai.Mayi.domain.Message;
import ai.Mayi.domain.Token;
import ai.Mayi.domain.User;
import ai.Mayi.domain.enums.MessageType;
import ai.Mayi.domain.enums.TokenType;
import ai.Mayi.repository.ChatRepository;
import ai.Mayi.repository.MessageRepository;
import ai.Mayi.repository.TokenRepository;
import ai.Mayi.web.dto.MessageDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    private final AIConfig aiConfig;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final TokenRepository tokenRepository;
    @Value("${gpt.model}")
    private String gptModel;

    @Value("${gpt.api.url}")
    private String gptUrl;

    public Message enterChat(Chat chat, String text) {
        Message message = Message.builder()
                .chat(chat)
                .messageType(MessageType.USER)
                .messageAt(LocalDateTime.now())
                .text(text)
                .build();

        return messageRepository.save(message);
    }

    @Async
    public CompletableFuture<MessageDTO.ChatResDTO> GPTService(@NotNull List<MessageType> aiTypeList, Message userMessage) {


        if (!aiTypeList.contains(MessageType.GPT)) {
            return null;
        }
        User user = userMessage.getChat().getUser();
        Token token = tokenRepository.findByUserAndTokenType(user, TokenType.GPT);

        if (token == null) {
            throw new TokenHandler(ErrorStatus._BAD_REQUEST);
        }

        //api call
        HttpHeaders headers = aiConfig.httpHeaders();
        headers.set("Authorization", "Bearer " + token.getTokenValue());

        MessageDTO.gptReqDTO chatRequest = new MessageDTO.gptReqDTO(gptModel,userMessage.getText());

        HttpEntity<MessageDTO.gptReqDTO> requestEntity = new HttpEntity<>(chatRequest, headers);

        RestTemplate restTemplate = new RestTemplate();
        MessageDTO.gptResDTO response = restTemplate.postForObject(gptUrl, requestEntity, MessageDTO.gptResDTO.class);

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new TokenHandler(ErrorStatus._GPT_RESPONSE_NULL);
        }

        //save response
        Message message = Message.builder()
                .chat(userMessage.getChat())
                .messageType(MessageType.GPT)
                .messageAt(LocalDateTime.now())
                .build();

        messageRepository.save(message);


        return CompletableFuture.completedFuture(MessageDTO.ChatResDTO.builder()
                .text(response.getChoices().get(0).getMessage().getContent())
                .messageType(MessageType.GPT)
                .build());
    }

    @Async
    public CompletableFuture<MessageDTO.ChatResDTO> CopliotService(@NotNull List<MessageType> aiTypeList, Message userMessage) {
        if (!aiTypeList.contains(MessageType.COPLIOT)) {
            return null;
        }

        HttpHeaders headers = aiConfig.httpHeaders();



        return CompletableFuture.completedFuture(MessageDTO.ChatResDTO.builder()
                .text("")
                .messageType(MessageType.COPLIOT)
                .build());
    }

    @Async
    public CompletableFuture<MessageDTO.ChatResDTO> BardService(@NotNull List<MessageType> aiTypeList, Message userMessage) {
        if (!aiTypeList.contains(MessageType.BARD)) {
            return null;
        }

        return CompletableFuture.completedFuture(MessageDTO.ChatResDTO.builder()
                .text("")
                .messageType(MessageType.BARD)
                .build());
    }

    @Async
    public CompletableFuture<MessageDTO.ChatResDTO> ClaudeService(@NotNull List<MessageType> aiTypeList, Message userMessage) {
        if (!aiTypeList.contains(MessageType.CLAUDE)) {
            return null;
        }

        return CompletableFuture.completedFuture(MessageDTO.ChatResDTO.builder()
                .text("")
                .messageType(MessageType.CLAUDE)
                .build());
    }

}