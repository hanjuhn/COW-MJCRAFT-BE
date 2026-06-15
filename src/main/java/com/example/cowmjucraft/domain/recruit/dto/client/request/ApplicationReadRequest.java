package com.example.cowmjucraft.domain.recruit.dto.client.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApplicationReadRequest {
    private Long formId;
    private String studentId;
    private String password;
}
