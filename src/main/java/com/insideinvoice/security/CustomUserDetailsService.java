package com.insideinvoice.security;

import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        // Exact match first (existing behavior for every legacy account), then a
        // case-insensitive fallback so a lowercase-normalized registration still
        // signs in when the user retypes their email in any casing. V39's
        // LOWER() unique indexes make the fallback unambiguous.
        User user = userRepository.findByEmailOrUsername(login)
                .or(() -> userRepository.findByEmailOrUsernameIgnoreCase(login))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email or username: " + login));
        return UserPrincipal.create(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return UserPrincipal.create(user);
    }
}
