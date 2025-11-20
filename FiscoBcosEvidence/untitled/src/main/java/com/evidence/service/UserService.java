package com.evidence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.evidence.dto.LoginRequest;
import com.evidence.dto.RegisterRequest;
import com.evidence.entity.User;
import com.evidence.vo.LoginResponse;
import com.evidence.vo.RegisterResponse;

public interface UserService extends IService<User> {

    LoginResponse login(LoginRequest loginRequest);

    RegisterResponse register(RegisterRequest registerRequest);

    User getUserByUsername(String username);

    User getUserByDID(String did);

    User getCurrentUser();

    boolean existsByUsername(String username);

    boolean existsByDID(String did);

    boolean existsByEmail(String email);
}