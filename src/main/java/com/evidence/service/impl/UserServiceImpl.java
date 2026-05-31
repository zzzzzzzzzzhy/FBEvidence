package com.evidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evidence.common.ResultCode;
import com.evidence.dto.LoginRequest;
import com.evidence.dto.RegisterRequest;
import com.evidence.entity.User;
import com.evidence.mapper.UserMapper;
import com.evidence.service.UserService;
import com.evidence.util.DIDUtil;
import com.evidence.util.JwtUtil;
import com.evidence.util.RedisCacheUtil;
import com.evidence.util.RedisLockUtil;
import com.evidence.vo.LoginResponse;
import com.evidence.vo.RegisterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;
    private final DIDUtil didUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final RedisLockUtil redisLockUtil;

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String loginIdentifier = loginRequest.getUsername();
        
        // 检查登录失败次数
        long failCount = redisCacheUtil.getLoginFailCount(loginIdentifier);
        if (failCount >= 5) {
            throw new RuntimeException("登录失败次数过多，账号已被锁定30分钟");
        }
        
        try {
            // 支持用户名或DID登录
            User user = null;
            
            // 判断是DID还是用户名
            if (didUtil.isValidDID(loginIdentifier)) {
                user = getUserByDID(loginIdentifier);
            } else {
                user = getUserByUsername(loginIdentifier);
            }
            
            if (user == null) {
                // 增加登录失败次数
                redisCacheUtil.incrementLoginFailCount(loginIdentifier);
                throw new RuntimeException("用户不存在");
            }

            // 验证密码
            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                // 增加登录失败次数
                redisCacheUtil.incrementLoginFailCount(loginIdentifier);
                throw new RuntimeException("密码错误");
            }

            // 检查用户状态
            if (user.getStatus() != 1) {
                throw new RuntimeException("用户已被禁用");
            }

            // 登录成功，清除失败次数
            redisCacheUtil.clearLoginFailCount(loginIdentifier);
            
            // 缓存用户信息
            redisCacheUtil.cacheUserInfo(user.getId(), user);
            
            // 更新用户在线状态
            redisCacheUtil.updateUserOnline(user.getId());

            // 生成JWT令牌
            String token = jwtUtil.generateToken(user.getUsername());

            log.info("用户登录成功: username={}, userId={}", user.getUsername(), user.getId());
            
            return new LoginResponse(user.getId(), token, user.getUsername(), user.getRealName(),
                    user.getEmail(), user.getDid(), jwtUtil.getExpirationTime());
        } catch (RuntimeException e) {
            log.warn("用户登录失败: loginIdentifier={}, reason={}", loginIdentifier, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        String username = registerRequest.getUsername();
        String email = registerRequest.getEmail();
        
        // 使用分布式锁防止并发注册
        String usernameLockKey = redisLockUtil.getRegisterLockKey(username);
        
        return redisLockUtil.executeWithLock(usernameLockKey, 30, () -> {
            // 验证密码一致性
            if (!registerRequest.isPasswordMatch()) {
                throw new RuntimeException("两次输入的密码不一致");
            }

            // 验证用户名格式
            if (!didUtil.isValidUsernameForDID(username)) {
                throw new RuntimeException("用户名格式不正确，只能包含字母、数字、下划线、中划线和中文");
            }

            // 检查用户名是否已存在
            if (existsByUsername(username)) {
                throw new RuntimeException("用户名已存在");
            }

            // 检查邮箱是否已存在（如果提供了邮箱）
            if (email != null && !email.trim().isEmpty()) {
                // 邮箱也需要加锁防止并发
                String emailLockKey = redisLockUtil.getEmailRegisterLockKey(email);
                boolean emailLocked = redisLockUtil.tryLock(emailLockKey, 
                    redisLockUtil.generateLockValue(), 30, java.util.concurrent.TimeUnit.SECONDS);
                
                try {
                    if (!emailLocked) {
                        throw new RuntimeException("邮箱正在被其他用户注册，请稍后重试");
                    }
                    
                    if (existsByEmail(email)) {
                        throw new RuntimeException("邮箱已被注册");
                    }
                    
                    // 使用DID生成锁确保DID生成的唯一性
                    String didLockKey = redisLockUtil.getDIDGenerateLockKey(username, email);
                    return redisLockUtil.executeWithLock(didLockKey, 30, () -> {
                        return doRegister(registerRequest);
                    });
                    
                } finally {
                    if (emailLocked) {
                        redisLockUtil.releaseLock(emailLockKey, redisLockUtil.generateLockValue());
                    }
                }
            } else {
                // 没有邮箱的情况，只需要DID生成锁
                String didLockKey = redisLockUtil.getDIDGenerateLockKey(username, null);
                return redisLockUtil.executeWithLock(didLockKey, 30, () -> {
                    return doRegister(registerRequest);
                });
            }
        });
    }
    
    /**
     * 执行实际的注册逻辑
     */
    private RegisterResponse doRegister(RegisterRequest registerRequest) throws Exception {
        // 生成DID
        String did = didUtil.generateDID(registerRequest.getUsername(), registerRequest.getEmail());
        
        // 确保DID唯一性（理论上不应该重复，但为了安全起见）
        int retryCount = 0;
        while (existsByDID(did) && retryCount < 5) {
            did = didUtil.generateDID(registerRequest.getUsername() + retryCount, registerRequest.getEmail());
            retryCount++;
        }
        
        if (existsByDID(did)) {
            throw new RuntimeException("DID生成失败，请稍后重试");
        }

        // 创建用户对象
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRealName(registerRequest.getRealName());
        user.setEmail(registerRequest.getEmail());
        user.setDid(did);
        user.setStatus(1); // 默认启用

        // 保存用户
        if (!save(user)) {
            throw new RuntimeException("用户注册失败");
        }

        log.info("用户注册成功: username={}, did={}", user.getUsername(), user.getDid());

        // 返回注册结果
        RegisterResponse response = new RegisterResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setDid(user.getDid());
        response.setRealName(user.getRealName());
        response.setEmail(user.getEmail());
        response.setRegisterTime(user.getCreatedAt() != null ? 
            user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
        response.setMessage("注册成功，您的DID是: " + did);

        return response;
    }

    @Override
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return getOne(wrapper);
    }

    @Override
    public User getUserByDID(String did) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getDid, did);
        return getOne(wrapper);
    }

    @Override
    public User getCurrentUser() {
        String currentUsername = jwtUtil.getCurrentUsername();
        if (currentUsername == null) {
            throw new RuntimeException("用户未登录");
        }
        
        User user = getUserByUsername(currentUsername);
        if (user != null) {
            // 更新用户在线状态
            redisCacheUtil.updateUserOnline(user.getId());
            
            // 缓存用户信息（如果还没有缓存的话）
            Object cachedUser = redisCacheUtil.getCachedUserInfo(user.getId());
            if (cachedUser == null) {
                redisCacheUtil.cacheUserInfo(user.getId(), user);
            }
        }
        
        return user;
    }

    @Override
    public boolean existsByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return count(wrapper) > 0;
    }

    @Override
    public boolean existsByDID(String did) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getDid, did);
        return count(wrapper) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email.trim());
        return count(wrapper) > 0;
    }
}