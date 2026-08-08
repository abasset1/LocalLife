package com.locallife.backend.user.application;

import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service User — minimal, simple délégation vers le repository.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String username, String email) {
        User user = new User(null, username, email, LocalDateTime.now());
        return userRepository.save(user);
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

}
