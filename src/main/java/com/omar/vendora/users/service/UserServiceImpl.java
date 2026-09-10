package com.omar.vendora.users.service;

import com.omar.vendora.common.exception.EmailAlreadyExistsException;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.dto.UserAuthDto;
import com.omar.vendora.users.dto.UserDto;
import com.omar.vendora.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public UserDto createUser(String email, String passwordHash, String fullName, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User(email, passwordHash, fullName, phone);

        try {
            User savedUser = userRepository.save(user);
            return new UserDto(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getPhone(),
                savedUser.getStatus().name(),
                savedUser.isAdmin(),
                savedUser.isCustomer(),
                savedUser.isSeller(),
                savedUser.getCreatedAt()
            );
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAuthDto> findByEmailForAuth(String email) {
        return userRepository.findByEmail(email)
            .map(user -> new UserAuthDto(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus().name(),
                user.isAdmin(),
                user.isCustomer(),
                user.isSeller()
            ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAuthDto> findByIdForAuth(UUID id) {
        return userRepository.findById(id)
            .map(user -> new UserAuthDto(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus().name(),
                user.isAdmin(),
                user.isCustomer(),
                user.isSeller()
            ));
    }
}
