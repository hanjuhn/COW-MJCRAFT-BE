package com.example.cowmjucraft.domain.recruit.dto.admin.response;

import lombok.Getter;

@Getter
public class AnswerItem {
    private Long formQuestionId;
    private String value;
    private String fileUrl;
    private String previewUrl;
    private String downloadUrl;
    private String fileName;

    public AnswerItem(Long formQuestionId, String value, String fileUrl) {
        this(formQuestionId, value, fileUrl, fileUrl, null, null);
    }

    public AnswerItem(
            Long formQuestionId,
            String value,
            String fileUrl,
            String previewUrl,
            String downloadUrl,
            String fileName
    ) {
        this.formQuestionId = formQuestionId;
        this.value = value;
        this.fileUrl = fileUrl;
        this.previewUrl = previewUrl;
        this.downloadUrl = downloadUrl;
        this.fileName = fileName;
    }
}
