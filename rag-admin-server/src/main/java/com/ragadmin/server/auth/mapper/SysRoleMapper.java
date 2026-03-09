package com.ragadmin.server.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragadmin.server.auth.entity.SysRoleEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRoleEntity> {

    @Select("""
            SELECT r.role_code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
