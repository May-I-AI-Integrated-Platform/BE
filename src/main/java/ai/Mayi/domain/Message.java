package ai.Mayi.domain;

import ai.Mayi.domain.enums.MessageType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Builder
@AllArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "message_at")
    private LocalDateTime messageAt;

    @Column(columnDefinition = "TEXT") // MySQL의 TEXT 타입으로 매핑
    private String text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id")
    private Chat chat;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL)
    private List<ModelMessage> modelMessages;

    public Message() {
        this.messageAt = LocalDateTime.now();
    }
}
