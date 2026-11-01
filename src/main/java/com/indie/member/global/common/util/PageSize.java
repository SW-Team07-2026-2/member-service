package com.indie.member.global.common.util;

import com.indie.member.global.config.MemberProperties;
import org.springframework.stereotype.Component;

/** size 파라미터 — 기본 20 · 최대 50 */
@Component
public class PageSize {

    private final MemberProperties.Feed props;

    public PageSize(MemberProperties props) {
        this.props = props.feed();
    }

    public int resolve(Integer requested) {
        if (requested == null || requested <= 0) return props.pageSize();
        return Math.min(requested, props.maxPageSize());
    }
}
