package com.example.cowmjucraft.domain.recruit.dto.client.request;

import com.example.cowmjucraft.domain.recruit.entity.DepartmentType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ApplicationUpdateRequest {

    private Long formId;
    private String studentId;
    private String password;

    private DepartmentType firstDepartment;
    private DepartmentType secondDepartment;

    private List<AnswerItemRequest> answers;

    @Getter
    @NoArgsConstructor
    public static class AnswerItemRequest {
        private Long formQuestionId;
        private String value;
    }
}
