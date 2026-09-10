package com.omar.vendora.identity.service;

import com.omar.vendora.identity.dto.LoginRequest;
import com.omar.vendora.identity.dto.LoginResponse;
import com.omar.vendora.identity.dto.LogoutRequest;
import com.omar.vendora.identity.dto.RefreshTokenRequest;
import com.omar.vendora.identity.dto.RefreshTokenResponse;
import com.omar.vendora.identity.dto.RegisterRequest;
import com.omar.vendora.identity.dto.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request, String clientIp);

    RefreshTokenResponse refresh(RefreshTokenRequest request);

    void logout(LogoutRequest request);
}
