package ezbuild.service;

import ezbuild.dto.SignUpForm;
import ezbuild.model.Address;
import ezbuild.model.Profile;
import ezbuild.model.Role;
import ezbuild.model.User;
import ezbuild.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    public static final int MAX_ADDRESSES = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User findByUsername(String username) {
        return username == null ? null : userRepository.findByUsernameIgnoreCase(username).orElse(null);
    }

    public User get(String id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found."));
    }

    public boolean usernameTaken(String username) {
        return userRepository.existsByUsernameIgnoreCase(username);
    }

    public boolean emailTaken(String email) {
        return userRepository.existsByEmailIgnoreCase(email);
    }

    public User register(SignUpForm form) {
        return register(form.getUsername(), form.getEmail(), form.getPassword());
    }

    public User register(String username, String email, String rawPassword) {
        if (usernameTaken(username)) throw new BusinessException("That username is taken.");
        if (emailTaken(email)) throw new BusinessException("An account with that email already exists.");
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim().toLowerCase(Locale.ROOT));
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRoles(EnumSet.of(Role.ROLE_USER));
        User saved = userRepository.save(user);
        log.info("New customer registered: user={}", saved.getUsername());
        return saved;
    }

    public void updateProfile(User user, Profile profile) {
        user.getProfile().setDisplayName(profile.getDisplayName());
        user.getProfile().setPhone(profile.getPhone());
        userRepository.save(user);
    }

    public void changePassword(User user, String current, String next) {
        if (!passwordEncoder.matches(current == null ? "" : current, user.getPassword())) {
            throw new BusinessException("Your current password is not correct.");
        }
        setPassword(user, next);
    }

    public void setPassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    /** Saves an address (or moves it to the top); the first one is the default. */
    public void saveAddress(User user, Address address) {
        user.getAddresses().removeIf(a -> a.oneLine().equalsIgnoreCase(address.oneLine()));
        user.getAddresses().add(0, address);
        while (user.getAddresses().size() > MAX_ADDRESSES) {
            user.getAddresses().remove(user.getAddresses().size() - 1);
        }
        userRepository.save(user);
    }

    public void removeAddress(User user, int index) {
        if (index >= 0 && index < user.getAddresses().size()) {
            user.getAddresses().remove(index);
            userRepository.save(user);
        }
    }

    public Page<User> search(String q, Pageable pageable) {
        String term = q == null ? "" : q.trim();
        return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term, pageable);
    }

    public void setSuspended(String userId, boolean suspended, String actor) {
        User user = get(userId);
        if (user.isAdmin() && suspended) throw new BusinessException("Admins cannot be suspended.");
        user.setSuspended(suspended);
        userRepository.save(user);
        log.info("Member {} suspended={} by {}", user.getUsername(), suspended, actor);
    }

    public void setRoles(String userId, Set<Role> roles, String actor) {
        User user = get(userId);
        Set<Role> next = roles == null || roles.isEmpty() ? EnumSet.of(Role.ROLE_USER) : EnumSet.copyOf(roles);
        next.add(Role.ROLE_USER);
        user.setRoles(next);
        userRepository.save(user);
        log.info("Member {} roles={} by {}", user.getUsername(), next, actor);
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}
