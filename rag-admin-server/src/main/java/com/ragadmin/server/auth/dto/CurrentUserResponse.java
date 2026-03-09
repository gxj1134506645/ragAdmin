package com.ragadmin.server.auth.dto;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String username,
        String displayName,
        String mobile,
        List<String> roles
) {
}
