package com.indie.member.domain.terms.service;

import com.indie.member.domain.terms.dto.request.TermsAgreement;
import com.indie.member.domain.terms.dto.response.MarketingView;
import com.indie.member.domain.terms.entity.AgreementItem;
import com.indie.member.domain.terms.entity.TermsCode;
import com.indie.member.domain.terms.entity.TermsEntity;
import com.indie.member.domain.terms.entity.UserTermsEntity;
import com.indie.member.domain.terms.entity.UserTermsEntity.Channel;
import com.indie.member.domain.terms.exception.TermsErrorCode;
import com.indie.member.domain.terms.repository.TermsRepository;
import com.indie.member.domain.terms.repository.UserTermsRepository;
import com.indie.member.global.common.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 약관 · 동의 (docs/25 §1-2 · §1-3). users ─< user_terms >─ terms · 이력은 INSERT 만.
 * 현재 버전 = 약관 코드마다 effective_at 이 지난 것 중 가장 늦은 것.
 */
@Service
public class TermsService {

    private final TermsRepository termsRepository;
    private final UserTermsRepository userTermsRepository;
    private final Clock clock;

    public TermsService(TermsRepository termsRepository, UserTermsRepository userTermsRepository, Clock clock) {
        this.termsRepository = termsRepository;
        this.userTermsRepository = userTermsRepository;
        this.clock = clock;
    }

    /** M24 — 동의받는 약관만, 코드마다 현재 버전 하나 · 가입 화면 순서 (TermsCode 선언 순서) */
    @Transactional(readOnly = true)
    public List<TermsEntity> current() {
        return termsRepository.findCurrent(TermsCode.consents(), now()).stream()
                .sorted(Comparator.comparing(TermsEntity::getCode)).toList();
    }

    /** M25 — 문서 하나의 현재 버전 (푸터 약관 화면 · 게시 문서 포함) */
    @Transactional(readOnly = true)
    public TermsEntity document(TermsCode code) {
        return termsRepository.findCurrent(List.of(code), now()).stream().findFirst()
                .orElseThrow(() -> new GeneralException(TermsErrorCode.TERMS_NOT_FOUND));   // 아직 시행 전 (effective_at 이 미래)
    }

    /**
     * 가입 동의 검사 — 🔴 사인업 토큰을 쓰기 전에 부릅니다 (실패해도 고쳐서 다시 보낼 수 있게).
     * 현재 약관마다 한 건으로 펴서 돌려줍니다 — 보내지 않은 선택 약관은 agreed=false.
     */
    @Transactional(readOnly = true)
    public List<TermsAgreement> checkSignup(List<TermsAgreement> agreements) {
        List<TermsEntity> current = current();
        Map<Long, TermsEntity> byId = current.stream().collect(Collectors.toMap(TermsEntity::getId, Function.identity()));
        Map<Long, Boolean> sent = new HashMap<>();
        for (TermsAgreement a : agreements == null ? List.<TermsAgreement>of() : agreements) {
            if (!byId.containsKey(a.termsId())) {
                throw new GeneralException(TermsErrorCode.TERMS_OUTDATED);   // 가입 도중 약관이 바뀜 · 없는 id
            }
            sent.put(a.termsId(), a.agreed());
        }
        if (current.stream().anyMatch(t -> t.isRequired() && !sent.getOrDefault(t.getId(), false))) {
            throw new GeneralException(TermsErrorCode.REQUIRED_AGREEMENT_MISSING);
        }
        return current.stream().map(t -> new TermsAgreement(t.getId(), sent.getOrDefault(t.getId(), false))).toList();
    }

    /** 가입 트랜잭션 안에서 — checkSignup 이 편 목록 그대로 */
    @Transactional
    public void recordSignup(long userId, List<TermsAgreement> agreements) {
        OffsetDateTime now = now();
        userTermsRepository.appendAll(agreements.stream()
                .map(a -> UserTermsEntity.of(userId, a.termsId(), a.agreed(), Channel.SIGNUP, now)).toList());
    }

    /** M17 — 약관 코드마다 마지막 기록 (버전이 바뀌어도 코드 기준) · 가입 화면 순서 */
    @Transactional(readOnly = true)
    public List<AgreementItem> myAgreements(long userId) {
        return userTermsRepository.findLatest(userId).stream()
                .sorted(Comparator.comparing(AgreementItem::code)).toList();
    }

    /** M18 — 현재 마케팅 약관으로 이력 1행 (channel=MY). 지금 값과 같으면 남기지 않고 마지막 기록을 돌려줍니다 */
    @Transactional
    public MarketingView changeMarketing(long userId, boolean agreed) {
        Optional<AgreementItem> last = myAgreements(userId).stream()
                .filter(a -> a.code() == TermsCode.MARKETING).findFirst();
        if (last.isPresent() && last.get().agreed() == agreed) {
            return new MarketingView(agreed, last.get().at());
        }
        OffsetDateTime now = now();
        userTermsRepository.append(UserTermsEntity.of(userId, document(TermsCode.MARKETING).getId(), agreed, Channel.MY, now));
        return new MarketingView(agreed, now);
    }

    /** DB(timestamptz) 정밀도와 같게 — 응답의 at 과 다시 읽은 at 이 같아야 합니다 */
    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
