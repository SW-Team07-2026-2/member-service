package com.indie.member.domain.user.service;

import com.indie.member.domain.user.dto.response.EmbeddingResponse;
import com.indie.member.domain.user.dto.response.EmbeddingSaved;
import com.indie.member.domain.user.repository.UserEmbeddingRepository;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/**
 * 회원 벡터 (users.embedding) — AI 서비스가 만들어 저장하고 읽습니다. 어떤 모델로 만들었는지는 AI 가 관리합니다.
 * 차원이 정해지기 전이라 TEXT 로 저장합니다. 형식만 pgvector 텍스트('[숫자,숫자,…]')로 맞춰 두면
 * 나중에 ALTER … TYPE vector(N) USING embedding::vector(N) 한 줄로 바꿀 수 있습니다. 숫자 개수는 검사하지 않습니다.
 */
@Service
public class EmbeddingService {

    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");

    private final UserEmbeddingRepository repository;
    private final Clock clock;

    public EmbeddingService(UserEmbeddingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EmbeddingResponse get(long userId) {
        return repository.findById(userId)
                .map(e -> new EmbeddingResponse(e.getId(), e.getEmbedding(), e.getEmbeddingUpdatedAt()))
                .orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
    }

    @Transactional
    public EmbeddingSaved save(long userId, String embedding) {
        String text = embedding.strip();
        int dimension = dimension(text);
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);   // DB(timestamptz) 정밀도와 같게
        if (repository.update(userId, text, now) == 0) {
            throw new GeneralException(CommonErrorCode.NOT_FOUND);
        }
        return new EmbeddingSaved(userId, dimension, now);
    }

    /** '[숫자,숫자,…]' 인지 확인하고 숫자 개수를 돌려줍니다. NaN · Infinity · float 범위를 넘는 값은 받지 않습니다 */
    static int dimension(String text) {
        if (text.length() < 2 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST);
        }
        String[] numbers = text.substring(1, text.length() - 1).split(",", -1);
        for (String raw : numbers) {
            String number = raw.strip();
            if (!NUMBER.matcher(number).matches() || !Float.isFinite(Float.parseFloat(number))) {
                throw new GeneralException(CommonErrorCode.INVALID_REQUEST);
            }
        }
        return numbers.length;
    }
}
