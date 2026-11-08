package com.vaccine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vaccine.common.BusinessException;
import com.vaccine.dto.LoginRequest;
import com.vaccine.dto.LoginResponse;
import com.vaccine.dto.RegisterUserRequest;
import com.vaccine.entity.InstitutionAdmin;
import com.vaccine.entity.Manufacturer;
import com.vaccine.entity.SuperAdmin;
import com.vaccine.entity.User;
import com.vaccine.mapper.InstitutionAdminMapper;
import com.vaccine.mapper.ManufacturerMapper;
import com.vaccine.mapper.SuperAdminMapper;
import com.vaccine.mapper.UserMapper;
import com.vaccine.security.JwtUtil;
import com.vaccine.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 账号与登录服务：4 角色登录、居民注册、当前登录人信息
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 账号不存在时也执行一次 BCrypt 匹配，避免响应时间差异泄露账号是否存在 */
    private static final String DUMMY_HASH = "$2a$10$P794GrCoyBB3j2vNbdjRv.lkyE4q1fG03CpMyKZdj1WOZmjAB5WPy";

    private static final String BAD_CREDENTIALS = "用户名或密码错误";

    private final UserMapper userMapper;
    private final InstitutionAdminMapper institutionAdminMapper;
    private final ManufacturerMapper manufacturerMapper;
    private final SuperAdminMapper superAdminMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /** 4 角色统一登录：按角色查表 → BCrypt 校验 → 签发 JWT */
    public LoginResponse login(LoginRequest request) {
        Role role;
        try {
            role = Role.valueOf(request.getRole().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("未知角色: " + request.getRole());
        }

        return switch (role) {
            case USER -> {
                User user = userMapper.selectOne(
                        new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
                checkPassword(user == null ? null : user.getPassword(), request.getPassword());
                yield build(user.getUserId(), user.getUsername(), user.getRealName(), role, null);
            }
            case INSTITUTION_ADMIN -> {
                InstitutionAdmin admin = institutionAdminMapper.selectOne(
                        new LambdaQueryWrapper<InstitutionAdmin>()
                                .eq(InstitutionAdmin::getUsername, request.getUsername()));
                checkPassword(admin == null ? null : admin.getPassword(), request.getPassword());
                yield build(admin.getAdminId(), admin.getUsername(), admin.getRealName(), role,
                        admin.getInstitutionId());
            }
            case MANUFACTURER -> {
                Manufacturer manufacturer = manufacturerMapper.selectOne(
                        new LambdaQueryWrapper<Manufacturer>()
                                .eq(Manufacturer::getUsername, request.getUsername()));
                checkPassword(manufacturer == null ? null : manufacturer.getPassword(), request.getPassword());
                yield build(manufacturer.getManufacturerId(), manufacturer.getUsername(),
                        manufacturer.getName(), role, null);
            }
            case SUPER_ADMIN -> {
                SuperAdmin superAdmin = superAdminMapper.selectOne(
                        new LambdaQueryWrapper<SuperAdmin>().eq(SuperAdmin::getUsername, request.getUsername()));
                checkPassword(superAdmin == null ? null : superAdmin.getPassword(), request.getPassword());
                yield build(superAdmin.getSuperAdminId(), superAdmin.getUsername(),
                        superAdmin.getRealName(), role, null);
            }
        };
    }

    /** 密码校验：账号不存在与密码错误返回同一提示（401），不泄露账号是否存在 */
    private void checkPassword(String storedHash, String rawPassword) {
        if (storedHash == null) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw BusinessException.unauthorized(BAD_CREDENTIALS);
        }
        if (!passwordEncoder.matches(rawPassword, storedHash)) {
            throw BusinessException.unauthorized(BAD_CREDENTIALS);
        }
    }

    private LoginResponse build(Long principalId, String username, String realName, Role role,
                                Long institutionId) {
        return LoginResponse.builder()
                .token(jwtUtil.generateToken(principalId, role))
                .role(role.name())
                .principalId(principalId)
                .username(username)
                .realName(realName)
                .institutionId(institutionId)
                .build();
    }

    /** 居民自助注册（密码 BCrypt 加密入库） */
    public User register(RegisterUserRequest request) {
        if (userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())) > 0) {
            throw BusinessException.badRequest("用户名已存在");
        }
        if (userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getIdCard, request.getIdCard())) > 0) {
            throw BusinessException.badRequest("身份证号已注册");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setIdCard(request.getIdCard());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender() == null ? 1 : request.getGender());
        user.setBirthDate(request.getBirthDate());
        userMapper.insert(user);
        return user;
    }

    /** 当前登录人信息（4 角色通用） */
    public Object me(Role role, Long principalId) {
        return switch (role) {
            case USER -> userMapper.selectById(principalId);
            case INSTITUTION_ADMIN -> institutionAdminMapper.selectById(principalId);
            case MANUFACTURER -> manufacturerMapper.selectById(principalId);
            case SUPER_ADMIN -> superAdminMapper.selectById(principalId);
        };
    }
}
