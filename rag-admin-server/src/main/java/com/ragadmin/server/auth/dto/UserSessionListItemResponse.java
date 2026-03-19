package com.ragadmin.server.auth.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class UserSessionListItemResponse {

    private Long userId;
    private String username;
    private String displayName;
    private String mobile;
    private String status;
    private List<String> roles;
    private Boolean adminOnline;
    private Boolean appOnline;
    private LocalDateTime lastLoginAt;
    private LocalDateTime lastActiveAt;
}
