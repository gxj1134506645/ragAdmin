package com.ragadmin.server.auth.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class UserListItemResponse {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String mobile;
    private String status;
    private List<String> roles;
    private Boolean adminOnline;
    private Boolean appOnline;
}
