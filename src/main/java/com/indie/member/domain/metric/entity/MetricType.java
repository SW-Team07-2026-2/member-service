package com.indie.member.domain.metric.entity;

/**
 * 행동 종류 — user_metric.metric_type (ck_user_metric_type 과 같은 값). metric_value 의 단위는 종류마다 다릅니다.
 * 11/24 서버가 API 호출 · 이벤트로 남기는 종류(server=true)는 프론트가 보내도 저장하지 않습니다 — 같은 행동을 두 번 세지 않게.
 * 콜렉션(보유 음반)은 메트릭으로 받지 않습니다.
 */
public enum MetricType {
    DETAIL_VIEW(false),            // 상세 화면 진입 — 1
    DWELL(false),                  // 상세 화면에 머문 시간 — ms
    PREVIEW_PLAY(false),           // 미리듣기 재생 시간 — ms
    SEARCH_CLICK(false),           // 검색 결과 클릭 — 결과 순위 (properties.keyword)
    RECOMMEND_CLICK(false),        // 추천 카드 클릭 — 1 (properties.source)
    CART_ADD(true),                // 장바구니 담기 — 늘어난 수량 (properties.optionId) · Catalog
    CART_REMOVE(true),             // 장바구니 빼기 — 줄어든 수량 (properties.optionId) · Catalog
    SHARE(false),                  // 공유 — 1
    EXTERNAL_LINK_CLICK(false),    // 외부 구매처 링크 클릭 — 1
    SEARCH(true),                  // 검색어 — 1 (target_id 없음 · target_type 은 검색한 탭 · properties.keyword) · Catalog · Concert
    FOLLOW(true),                  // 아티스트 팔로우 — 1 · member.activity
    UNFOLLOW(true),                // 아티스트 팔로우 취소 — 1 · member.activity
    PURCHASE(true),                // 구매 확정 — 수량 (properties.orderId · optionId) · order.confirmed
    BOOKING(true);                 // 예매 완료 — 좌석 수 (properties.bookingId) · booking.completed

    private final boolean server;

    MetricType(boolean server) {
        this.server = server;
    }

    /** 서버만 기록하는 종류 — 수집 API(프론트)로 오면 그 건만 버립니다 */
    public boolean server() {
        return server;
    }
}
