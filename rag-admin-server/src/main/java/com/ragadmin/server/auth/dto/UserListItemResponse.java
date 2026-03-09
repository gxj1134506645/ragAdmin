package com.ragadmin.server.auth.dto;

import java.util.List;

public record UserListItemResponse(
        Long id,
        String username,
        String displayName,
        String email,
        String mobile,
        String status,
        List<String> roles
) {
}
