package com.example.cowmjucraft.domain.recruit.exception;

import com.example.cowmjucraft.global.response.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecruitErrorType implements ErrorCode {

    FORM_NOT_FOUND(404, "폼을 찾을 수 없습니다."),
    FORM_QUESTION_NOT_FOUND(404, "문항을 찾을 수 없습니다."),
    TARGET_FORM_NOT_FOUND(404, "대상 폼을 찾을 수 없습니다."),
    SOURCE_FORM_NOT_FOUND(404, "원본 폼을 찾을 수 없습니다."),
    NOTICE_NOT_FOUND(404, "공지사항을 찾을 수 없습니다."),
    APPLICATION_NOT_FOUND(404, "지원서를 찾을 수 없습니다."),

    INVALID_CREDENTIALS(401, "학번 또는 비밀번호가 올바르지 않습니다."),

    NOTICE_NOT_IN_THIS_FORM(400, "해당 폼에 속한 공지사항이 아닙니다."),
    SOURCE_FORM_ID_REQUIRED(400, "sourceFormId가 필요합니다."),
    SOURCE_AND_TARGET_CANNOT_BE_SAME(400, "sourceFormId와 targetFormId는 같을 수 없습니다."),
    COMMON_SECTION_CANNOT_HAVE_DEPARTMENT(400, "기본/공통 섹션에는 부서가 있을 수 없습니다."),
    SELECT_OPTIONS_ONLY_FOR_SELECT(400, "선택형 문항에만 선택 옵션을 설정할 수 있습니다."),
    DEPARTMENT_TYPE_REQUIRED_FOR_DEPARTMENT_SECTION(400, "부서 섹션에는 부서 타입이 필요합니다."),
    APPLICATION_NOT_IN_THIS_FORM(400, "해당 폼에 속한 지원서가 아닙니다."),
    FIRST_SECOND_DEPARTMENT_MUST_BE_DIFFERENT(400, "1지망과 2지망은 서로 달라야 합니다."),
    BOTH_DEPARTMENTS_REQUIRED(400, "1지망과 2지망을 모두 입력해야 합니다."),
    FORM_QUESTION_ID_REQUIRED(400, "formQuestionId가 필요합니다."),
    DUPLICATE_ANSWER(400, "중복된 답변이 존재합니다."),
    REQUIRED_ANSWER_MISSING(400, "필수 문항의 답변이 누락되었습니다."),
    ANSWER_FOR_UNSELECTED_DEPARTMENT(400, "선택하지 않은 부서의 문항에 대한 답변입니다."),
    INVALID_SECTION_OR_DEPARTMENT_TYPE(400, "섹션 또는 부서 정보가 올바르지 않습니다."),
    FORM_QUESTION_NOT_IN_THIS_FORM(400, "해당 폼에 속한 문항이 아닙니다."),
    REQUIRED_ANSWER_CANNOT_BE_DELETED(400, "필수 답변은 삭제할 수 없습니다."),
    CANNOT_DELETE_OPEN_FORM(400,"활성화되어 있는 폼은 삭제할 수 없습니다."),
    CANNOT_DELETE_FORM_WITH_APPLICATIONS(400,"지원서가 존재하는 폼은 삭제할 수 없습니다"),

    RECRUITMENT_CLOSED(409, "모집이 마감되었습니다."),
    DUPLICATE_STUDENT_ID(409, "이미 제출된 학번입니다."),
    DUPLICATE_QUESTION_ORDER(409, "문항 순서가 중복되었습니다.");

    private final int httpStatusCode;
    private final String message;
}
