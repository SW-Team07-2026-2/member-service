package com.indie.member.domain.user.service;

import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.repository.ChatHistoryRepository;
import com.indie.member.global.common.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHistoryServiceUnitTest {

    private final ChatHistoryRepository repository = mock(ChatHistoryRepository.class);
    private final ChatHistoryService service = new ChatHistoryService(repository);

    @Test
    void appendLocksMemberThenInsertsTrimsTo50AndReturnsStored() {
        when(repository.lockUser(42)).thenReturn(true);
        when(repository.findRecent(42, 50)).thenReturn(messages(2, 52));
        List<ChatMessage> incoming = messages(50, 52);

        assertThat(service.append(42, incoming)).containsExactlyElementsOf(messages(2, 52));
        InOrder order = inOrder(repository);
        order.verify(repository).lockUser(42);
        order.verify(repository).insert(42, incoming);
        order.verify(repository).deleteAllButRecent(42, 50);
        order.verify(repository).findRecent(42, 50);
    }

    @Test
    void initialHistoryIsEmptyAndReadDoesNotLockOrWrite() {
        when(repository.userExists(42)).thenReturn(true);
        when(repository.findRecent(42, 50)).thenReturn(List.of());
        assertThat(service.get(42)).isEmpty();
        verify(repository, never()).lockUser(anyLong());
        verifyNoWrites();
    }

    /** 탈퇴한 회원은 행이 없습니다 — 읽기 · 쓰기 모두 404 */
    @Test
    void missingMemberCannotReadOrWrite() {
        when(repository.userExists(42)).thenReturn(false);
        when(repository.lockUser(42)).thenReturn(false);
        assertCode(() -> service.get(42), "NOT_FOUND");
        assertCode(() -> service.append(42, messages(0, 1)), "NOT_FOUND");
        verify(repository, never()).findRecent(anyLong(), anyInt());
        verifyNoWrites();
    }

    private void verifyNoWrites() {
        verify(repository, never()).insert(anyLong(), anyList());
        verify(repository, never()).deleteAllButRecent(anyLong(), anyInt());
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
