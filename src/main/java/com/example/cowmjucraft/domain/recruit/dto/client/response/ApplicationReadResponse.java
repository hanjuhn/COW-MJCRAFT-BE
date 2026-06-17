package com.example.cowmjucraft.domain.recruit.dto.client.response;

import com.example.cowmjucraft.domain.recruit.entity.DepartmentType;
import java.time.LocalDateTime;
import java.util.List;

public record ApplicationReadResponse(
        boolean editable,

        Long applicationId,

        String studentId,

        DepartmentType firstDepartment,
        DepartmentType secondDepartment,

        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        NoticeItem basicNotice,
        NoticeItem commonNotice,
        NoticeItem firstDepartmentNotice,
        NoticeItem secondDepartmentNotice,

        List<AnswerItem> basicAnswers,
        List<AnswerItem> commonAnswers,
        List<AnswerItem> firstDepartmentAnswers,
        List<AnswerItem> secondDepartmentAnswers,

        List<ApplicationFormInfoResponse.NoticeDto> notices,
        List<ApplicationFormInfoResponse.QuestionDto> questions

) {
    public record AnswerItem(
            Long formQuestionId,
            String value,
            String fileKey,
            String fileUrl,
            String fileName
    ) {
        public AnswerItem(Long formQuestionId, String value) {
            this(formQuestionId, value, null, null, null);
        }
    }

    public record NoticeItem(
            String title,
            String content
    ) {}
}
