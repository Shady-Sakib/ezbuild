package ezbuild.security;

import ezbuild.model.Authority;
import ezbuild.model.Role;
import ezbuild.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** User roles -> Spring authorities: ROLE_* (hasRole) plus the fine-grained ones (hasAuthority). */
public final class Authorities {

    private Authorities() { }

    public static List<GrantedAuthority> of(User user) {
        List<GrantedAuthority> list = new ArrayList<>();
        for (Role role : user.getRoles()) {
            list.add(new SimpleGrantedAuthority(role.name()));
        }
        for (Authority a : authoritySet(user)) {
            list.add(new SimpleGrantedAuthority(a.name()));
        }
        return list;
    }

    public static Set<Authority> authoritySet(User user) {
        Set<Authority> set = new LinkedHashSet<>();
        user.getRoles().forEach(r -> set.addAll(r.getAuthorities()));
        return set;
    }
}
