package com.example.cowmjucraft.domain.recruit.dto.admin.response;

import com.example.cowmjucraft.domain.recruit.entity.DepartmentType;
import com.example.cowmjucraft.domain.recruit.entity.ResultStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApplicationListAdminResponse {
    private Long applicationId;

    private String studentId;
    private String applicantName;

    private DepartmentType firstDepartment;
    private DepartmentType secondDepartment;

    private ResultStatus resultStatus;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
