package com.indie.member.domain.metric.entity;

/**
 * 행동 종류 — user_metric.metric_type (ck_user_metric_type 과 같은 값). metric_value 의 단위는 종류마다 다릅니다.
 * 팔로우 · 콜렉션 · 구매 · 예매는 각 테이블(서비스)에 원본이 있어 메트릭으로 받지 않습니다.
 */
public enum MetricType {
    DETAIL_VIEW,            // 상세 화면 진입 — 1
    DWELL,                  // 상세 화면에 머문 시간 — ms
    PREVIEW_PLAY,           // 미리듣기 재생 시간 — ms
    SEARCH_CLICK,           // 검색 결과 클릭 — 결과 순위 (properties.keyword)
    RECOMMEND_CLICK,        // 추천 카드 클릭 — 1 (properties.source)
    CART_ADD,               // 장바구니 담기 — 수량 (properties.optionId)
    CART_REMOVE,            // 장바구니 빼기 — 수량
    SHARE,                  // 공유 — 1
    EXTERNAL_LINK_CLICK     // 외부 구매처 링크 클릭 — 1
}
