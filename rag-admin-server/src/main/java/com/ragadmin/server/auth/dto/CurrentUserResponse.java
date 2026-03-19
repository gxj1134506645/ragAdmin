package com.ragadmin.server.auth.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class CurrentUserResponse {

    private Long id;
    private String username;
    private String displayName;
    private String mobile;
    private List<String> roles;
    private List<String> permissions;
}
