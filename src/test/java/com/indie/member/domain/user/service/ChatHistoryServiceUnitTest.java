package com.indie.member.domain.user.service;

import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.user.repository.ChatHistoryRepository;
import com.indie.member.global.common.exception.GeneralException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatHistoryServiceUnitTest {

    private final ChatHistoryRepository repository = mock(ChatHistoryRepository.class);
    private final ChatHistoryService service = new ChatHistoryService(repository);

    @Test
    void appendRetainsLast50AndDoesNotSortBySpokenTime() {
        when(repository.find(42, true)).thenReturn(Optional.of(new ChatHistoryRepository.History(
                UserEntity.Status.ACTIVE, messages(0, 50))));
        List<ChatMessage> retained = service.append(42, messages(50, 52));
        assertThat(retained).containsExactlyElementsOf(messages(2, 52));
        verify(repository).save(42, retained);
    }

    @Test
    void initialHistoryIsEmptyAndReadDoesNotWrite() {
        when(repository.find(42, false)).thenReturn(Optional.of(new ChatHistoryRepository.History(UserEntity.Status.ACTIVE, List.of())));
        assertThat(service.get(42)).isEmpty();
        verify(repository, never()).save(anyLong(), anyList());
    }

    @Test
    void missingMemberCannotReadOrWrite() {
        when(repository.find(eq(42L), anyBoolean())).thenReturn(Optional.empty());
        assertCode(() -> service.get(42), "NOT_FOUND");
        assertCode(() -> service.append(42, messages(0, 1)), "NOT_FOUND");
        verify(repository, never()).save(anyLong(), anyList());
    }

    @Test
    void withdrawnMemberCannotReadOrWrite() {
        when(repository.find(eq(42L), anyBoolean())).thenReturn(Optional.of(new ChatHistoryRepository.History(
                UserEntity.Status.WITHDRAWN, messages(0, 1))));
        assertCode(() -> service.get(42), "ACCOUNT_WITHDRAWN");
        assertCode(() -> service.append(42, messages(1, 2)), "ACCOUNT_WITHDRAWN");
        verify(repository, never()).save(anyLong(), anyList());
    }

    private static List<ChatMessage> messages(int from, int to) {
        return IntStream.range(from, to).mapToObj(i -> new ChatMessage(ChatMessage.Role.USER, "대화 " + i,
                OffsetDateTime.parse("2026-11-06T15:30:00+09:00").minusSeconds(i))).toList();
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
