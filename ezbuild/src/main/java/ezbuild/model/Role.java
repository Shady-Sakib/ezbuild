package ezbuild.model;

import java.util.Set;

public enum Role {
    ROLE_ADMIN("Admin", Set.of(Authority.PRODUCT_WRITE, Authority.ORDER_MANAGE, Authority.ORDER_REFUND,
            Authority.VIEW_REPORTS, Authority.MEMBER_MANAGE)),
    ROLE_STAFF("Staff", Set.of(Authority.PRODUCT_WRITE, Authority.ORDER_MANAGE, Authority.VIEW_REPORTS)),
    ROLE_USER("Customer", Set.of());

    private final String label;
    private final Set<Authority> authorities;

    Role(String label, Set<Authority> authorities) {
        this.label = label;
        this.authorities = authorities;
    }

    public String getLabel() {
        return label;
    }

    public Set<Authority> getAuthorities() {
        return authorities;
    }
}
