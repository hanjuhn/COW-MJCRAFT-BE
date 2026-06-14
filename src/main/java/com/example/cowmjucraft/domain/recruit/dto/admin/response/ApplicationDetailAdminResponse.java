package com.example.cowmjucraft.domain.recruit.dto.admin.response;

import com.example.cowmjucraft.domain.recruit.entity.DepartmentType;
import com.example.cowmjucraft.domain.recruit.entity.ResultStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class ApplicationDetailAdminResponse {

    private Long applicationId;

    private String studentId;

    private DepartmentType firstDepartment;
    private DepartmentType secondDepartment;

    private ResultStatus resultStatus;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<AnswerItem> basicAnswers;
    private List<AnswerItem> commonAnswers;
    private List<AnswerItem> firstDepartmentAnswers;
    private List<AnswerItem> secondDepartmentAnswers;
}
