package com.evidence.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    
    @Mock
    private RedisCacheUtil redisCacheUtil;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock the blacklist check to return false by default
        when(redisCacheUtil.isTokenBlacklisted(anyString())).thenReturn(false);
        
        jwtUtil = new JwtUtil(redisCacheUtil);
    }

    @Test
    void testGenerateToken() {
        String username = "testuser";

        String token = jwtUtil.generateToken(username);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGetUsernameFromToken() {
        String username = "testuser";
        String token = jwtUtil.generateToken(username);

        String extractedUsername = jwtUtil.getUsernameFromToken(token);

        assertEquals(username, extractedUsername);
    }

    @Test
    void testValidateToken() {
        String username = "testuser";
        String token = jwtUtil.generateToken(username);

        boolean isValid = jwtUtil.validateToken(token);

        assertTrue(isValid);
    }

    @Test
    void testValidateInvalidToken() {
        String invalidToken = "invalid.token.here";

        boolean isValid = jwtUtil.validateToken(invalidToken);

        assertFalse(isValid);
    }

    @Test
    void testGetUsernameFromInvalidToken() {
        String invalidToken = "invalid.token.here";

        String username = jwtUtil.getUsernameFromToken(invalidToken);

        assertNull(username);
    }

    @Test
    void testGetExpirationTime() {
        Long expirationTime = jwtUtil.getExpirationTime();

        assertNotNull(expirationTime);
        assertTrue(expirationTime > 0);
    }
}