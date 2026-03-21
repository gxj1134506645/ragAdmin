package com.ragadmin.server.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.auth.dto.CreateUserRequest;
import com.ragadmin.server.auth.dto.UpdateUserRequest;
import com.ragadmin.server.auth.dto.UserListItemResponse;
import com.ragadmin.server.auth.entity.SysRoleEntity;
import com.ragadmin.server.auth.entity.SysUserEntity;
import com.ragadmin.server.auth.entity.SysUserRoleEntity;
import com.ragadmin.server.auth.mapper.AuthUserStructMapper;
import com.ragadmin.server.auth.mapper.SysRoleMapper;
import com.ragadmin.server.auth.mapper.SysUserMapper;
import com.ragadmin.server.auth.mapper.SysUserRoleMapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserAdminService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private AuthUserStructMapper authUserStructMapper;

    @Resource
    private SaTokenLoginService saTokenLoginService;

    public PageResponse<UserListItemResponse> list(String keyword, String status, long pageNo, long pageSize) {
        Page<SysUserEntity> page = sysUserMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<SysUserEntity>()
                        .eq(StringUtils.hasText(status), SysUserEntity::getStatus, status)
                        .eq(SysUserEntity::getDeleted, Boolean.FALSE)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysUserEntity::getUsername, keyword)
                                .or()
                                .like(SysUserEntity::getDisplayName, keyword)
                                .or()
                                .like(SysUserEntity::getMobile, keyword))
                        .orderByDesc(SysUserEntity::getId)
        );
        if (page.getRecords().isEmpty()) {
            return new PageResponse<>(Collections.emptyList(), pageNo, pageSize, page.getTotal());
        }

        Map<Long, List<String>> rolesByUserId = page.getRecords().stream()
                .collect(Collectors.toMap(SysUserEntity::getId, user -> sysRoleMapper.selectRoleCodesByUserId(user.getId())));

        return new PageResponse<>(
                page.getRecords().stream()
                        .map(user -> toResponse(user, rolesByUserId.getOrDefault(user.getId(), List.of())))
                        .toList(),
                pageNo,
                pageSize,
                page.getTotal()
        );
    }

    @Transactional
    public UserListItemResponse create(CreateUserRequest request) {
        validateUniqueUser(request.getUsername(), request.getMobile());

        SysUserEntity user = new SysUserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setStatus(request.getStatus());
        user.setDeleted(Boolean.FALSE);
        sysUserMapper.insert(user);

        // 用户新增时如果带了角色，直接在同一事务里写关联，避免出现“用户已创建但无角色”的半成状态。
        List<String> roleCodes = request.getRoleCodes() == null ? List.of() : request.getRoleCodes().stream().distinct().toList();
        if (!roleCodes.isEmpty()) {
            bindRoles(user.getId(), roleCodes);
        }

        return toResponse(user, roleCodes);
    }

    @Transactional
    public UserListItemResponse update(Long userId, UpdateUserRequest request) {
        SysUserEntity user = requireUser(userId);
        validateUniqueMobileForUpdate(userId, request.getMobile());

        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setStatus(request.getStatus());
        // 密码更新走显式字段，避免每次普通资料更新都触发密码重算。
        if (StringUtils.hasText(request.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        sysUserMapper.updateById(user);
        return toResponse(user, sysRoleMapper.selectRoleCodesByUserId(user.getId()));
    }

    @Transactional
    public void assignRoles(Long userId, List<String> roleCodes) {
        requireUser(userId);

        // 角色重配采用“先删后插”，逻辑直白且适合当前单表关联场景。
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getUserId, userId));
        bindRoles(userId, roleCodes.stream().distinct().toList());
    }

    private void bindRoles(Long userId, List<String> roleCodes) {
        List<SysRoleEntity> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .in(SysRoleEntity::getRoleCode, roleCodes));
        Map<String, SysRoleEntity> roleMap = roles.stream()
                .collect(Collectors.toMap(SysRoleEntity::getRoleCode, Function.identity()));
        for (String roleCode : roleCodes) {
            SysRoleEntity role = roleMap.get(roleCode);
            if (role == null) {
                throw new BusinessException("ROLE_NOT_FOUND", "角色不存在: " + roleCode, HttpStatus.BAD_REQUEST);
            }
            SysUserRoleEntity relation = new SysUserRoleEntity();
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            sysUserRoleMapper.insert(relation);
        }
    }

    private void validateUniqueUser(String username, String mobile) {
        SysUserEntity usernameExists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getUsername, username)
                .last("LIMIT 1"));
        if (usernameExists != null) {
            throw new BusinessException("USERNAME_EXISTS", "用户名已存在", HttpStatus.BAD_REQUEST);
        }
        if (StringUtils.hasText(mobile)) {
            SysUserEntity mobileExists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                    .eq(SysUserEntity::getMobile, mobile)
                    .last("LIMIT 1"));
            if (mobileExists != null) {
                throw new BusinessException("MOBILE_EXISTS", "手机号已存在", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void validateUniqueMobileForUpdate(Long userId, String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return;
        }
        SysUserEntity mobileExists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getMobile, mobile)
                .ne(SysUserEntity::getId, userId)
                .last("LIMIT 1"));
        if (mobileExists != null) {
            throw new BusinessException("MOBILE_EXISTS", "手机号已存在", HttpStatus.BAD_REQUEST);
        }
    }

    private SysUserEntity requireUser(Long userId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        return user;
    }

    private UserListItemResponse toResponse(SysUserEntity user, List<String> roles) {
        return authUserStructMapper.toUserListItemResponse(user, roles)
                // 用户列表直接补齐前后台在线状态，避免分页表格为每一行额外发起会话详情查询。
                .setAdminOnline(saTokenLoginService.isLogin(user.getId(), AuthService.ADMIN_LOGIN_TYPE))
                .setAppOnline(saTokenLoginService.isLogin(user.getId(), AuthService.APP_LOGIN_TYPE));
    }
}
