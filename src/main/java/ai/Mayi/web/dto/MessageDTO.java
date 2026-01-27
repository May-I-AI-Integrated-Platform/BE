package ai.Mayi.web.dto;

import ai.Mayi.domain.enums.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

public class MessageDTO {
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "MESSAGE_REQ_01 : 채팅 입력 요청 DTO")
    public static class enterChatReqDTO {
        @NotNull
        private Long chatId;
        @NotNull
        private List<MessageType> aiTypeList;
        @NotNull
        private String text;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "MESSAGE_DTO_01 : 채팅 응답 DTO")
    public static class ChatResDTO {
        @NotNull
        private MessageType messageType;
        @NotNull
        private String text;
        @Builder.Default
        @Schema(description = "에러 여부 (Circuit Breaker OPEN 등)")
        private Boolean isError = false;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "MESSAGE_REQ_01 : 채팅 입력 응답 DTO")
    public static class enterChatResDTO {
        @NotNull
        private Long chatId;
        @NotNull
        private List<ChatResDTO> responseDTOList;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class gptChatMessage {
        private String role;
        private String content;
    }

    @Data
    public static class gptReqDTO {

        private String model;
        private List<gptChatMessage> messages;
        private int n;

        public gptReqDTO(String model, String message) {
            this.model = model;
            this.messages = new ArrayList<gptChatMessage>();
            this.messages.add(new gptChatMessage("user",message));

            this.n = 1;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class gptResDTO {

        private List<Choice> choices;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Choice {
            private int index;
            private gptChatMessage message;
        }
    }
    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "BARD_COMMON_01 : Bard DTO")
    public static class BardContents {
        private String role;
        private List<Parts> parts;
        @Data
        @Builder
        public static class Parts {
            private String text;
        }
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "BARD_REQ_01 : Bard 입력 요청 DTO")
    public static class BardChatReqDTO {
        @NotNull
        private List<BardContents> contents;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "BARD_RES_01 : Bard 입력 응답 DTO")
    public static class BardChatResDTO {
        @NotNull
        private List<Candidates> candidates;
        @NotNull
        private String modelVersion;
        @NotNull
        private Object usageMetadata;
        @Data
        public static class Candidates {
            private BardContents content;
            private String finishReason;
            private Double avgLogprobs;
        }
    }

    @Data
    public static class claudeResDto {
        private List<Content> content;
        private String id;
        private String model;
        private String role;
        private String type;

        @Data
        public static class Content {
            public String text;
            public String type;
        }
    }

    @Builder
    @Data
    public static class ClaudeReqDto {
        private String model;
        private List<Message> messages;
        private int max_tokens;

        @Data
        @Builder
        public static class Message {
            private String role;
            private String content;
        }
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "DEEPSEEK_COMMON_01 : DeepSek DTO")
    public static class DeepSeekMessage {
        @NotNull
        private String content;
        @NotNull
        private String role;
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "DEEPSEEK_REQ_01 : DeepSeek 입력 요청 DTO")
    public static class DeepSeekChatReqDTO {
        @NotNull
        private List<DeepSeekMessage> messages;
        @NotNull
        private String model;

    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "DEEPSEEK_RES_01 : DeepSeek 입력 응답 DTO")
    public static class DeepSeekChatResDTO {
        @NotNull
        private String id;
        @NotNull
        private String model;
        @NotNull
        private List<Choice> choices;

        @Data
        public static class Choice {
            @NotNull
            private Integer index;
            @NotNull
            private String finish_reason;
            @NotNull
            private DeepSeekMessage message;
        }
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "MESSAGE_RES_02 : 채팅 조회 응답 DTO")
    public static class getChatResDTO {
        Long chatId;
        String chatName;
        List<messageDTO> messages;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "MESSAGE_RES_02 : 채팅 조회 응답 DTO-1")
    public static class messageDTO {
        Boolean isUser;
        List<ChatResDTO> messages;
    }
}

