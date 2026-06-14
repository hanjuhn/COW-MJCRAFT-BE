package com.example.cowmjucraft.domain.recruit.controller.admin;

import com.example.cowmjucraft.domain.recruit.dto.admin.request.*; // NoticeRequest 등 포함되도록 와일드카드 사용 추천
import com.example.cowmjucraft.domain.recruit.dto.admin.response.*;
import com.example.cowmjucraft.global.response.ApiResult;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(
        name = "Recruit - Form (Admin)",
        description = "Form 관리자 API"
)
public interface FormAdminControllerDocs {

    @Operation(
            summary = "Form 생성",
            description = "모집 Form을 생성합니다. open=true로 생성하면 기존 open Form이 있으면 close 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(schema = @Schema(implementation = ApiResult.class))
            ),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    ResponseEntity<ApiResult<FormCreateAdminResponse>> createForm(
            @Parameter(description = "Form 생성 요청")
            FormCreateAdminRequest request
    );

    @Operation(summary = "폼 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "현재 활성화된 폼은 삭제할 수 없습니다. / 지원서가 존재하는 폼은 삭제할 수 없습니다."),
            @ApiResponse(responseCode = "404", description = "폼을 찾을 수 없습니다.")
    })
    ResponseEntity<ApiResult<Void>> deleteForm(
            @Parameter(description = "폼 ID", example = "1")
            @PathVariable Long formId
    );

    // ------------------------------------------------------------

    @Operation(summary = "Form OPEN", description = "지정한 Form을 OPEN 상태로 변경합니다. 다른 OPEN Form이 있으면 close 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "Form 없음")
    })
    ResponseEntity<ApiResult<Void>> openForm(@Parameter(description = "Form ID", example = "1") Long formId);

    @Operation(summary = "Form CLOSE", description = "지정한 Form을 CLOSE 상태로 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "Form 없음")
    })
    ResponseEntity<ApiResult<Void>> closeForm(@Parameter(description = "Form ID", example = "1") Long formId);

    // ------------------------------------------------------------

    @Operation(
            summary = "문항 추가",
            description = """
                    Form에 문항(FormQuestion)을 추가합니다.
                    - sectionType은 BASIC, COMMON, DEPARTMENT 중 하나입니다.
                    - sectionType=BASIC 또는 COMMON이면 departmentType은 null이어야 합니다.
                    - sectionType=DEPARTMENT이면 departmentType이 필요합니다.
                    - answerType!=SELECT이면 selectOptions는 null이어야 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "규칙 위반/잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "Form 없음")
    })
    ResponseEntity<ApiResult<AddQuestionAdminResponse>> addQuestion(
            @Parameter(description = "Form ID", example = "1") Long formId,
            @Parameter(description = "문항 추가 요청") AddQuestionAdminRequest request
    );

    // ------------------------------------------------------------

    @Operation(summary = "Form 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공")
    })
    ResponseEntity<ApiResult<List<FormListAdminResponse>>> getForms();

    @Operation(summary = "Form 단건 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "Form 없음")
    })
    ResponseEntity<ApiResult<FormDetailAdminResponse>> getForm(
            @Parameter(description = "Form ID", example = "1") Long formId
    );

    // ------------------------------------------------------------

    @Operation(summary = "Form 문항 목록 조회", description = "Form에 속한 문항(FormQuestion)을 questionOrder 오름차순으로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "Form 없음")
    })
    ResponseEntity<ApiResult<List<FormQuestionListAdminResponse>>> getFormQuestions(
            @Parameter(description = "Form ID", example = "1") Long formId
    );

    // ------------------------------------------------------------

    @Operation(summary = "Form 문항 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "Form 불일치"),
            @ApiResponse(responseCode = "404", description = "FormQuestion 없음")
    })
    ResponseEntity<ApiResult<Void>> deleteFormQuestion(
            @Parameter(description = "Form ID", example = "1") Long formId,
            @Parameter(description = "FormQuestion ID", example = "10") Long formQuestionId
    );

    @Operation(summary = "Form 문항 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "규칙 위반/Form 불일치"),
            @ApiResponse(responseCode = "404", description = "FormQuestion 없음")
    })
    ResponseEntity<ApiResult<Void>> updateFormQuestion(
            @Parameter(description = "Form ID", example = "1") Long formId,
            @Parameter(description = "FormQuestion ID", example = "10") Long formQuestionId,
            @Parameter(description = "문항 수정 요청") FormQuestionUpdateAdminRequest request
    );

    // ------------------------------------------------------------

    @Operation(summary = "문항 복사(덮어쓰기)", description = "sourceFormId의 문항과 안내문을 targetFormId로 복사하며, target의 기존 문항/안내문은 전부 삭제 후 복사합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "요청 규칙 위반(같은 form 복사 등)"),
            @ApiResponse(responseCode = "404", description = "source/target Form 없음")
    })
    ResponseEntity<ApiResult<FormCopyAdminResponse>> copyFormQuestionsOverwrite(
            @Parameter(description = "대상 Form ID", example = "2") Long targetFormId,
            @Parameter(description = "복사 요청") FormCopyAdminRequest request
    );

    // ------------------------------------------------------------
    // [추가된 부분] Notice(안내문) 관련 API
    // ------------------------------------------------------------

    @Operation(
            summary = "안내문 추가",
            description = """
                Form에 안내문(Notice)을 추가합니다.
                - sectionType은 BASIC, COMMON, DEPARTMENT 중 하나입니다.
                - sectionType=BASIC 또는 COMMON이면 departmentType은 null이어야 합니다.
                - sectionType=DEPARTMENT이면 departmentType이 필요합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "규칙 위반/잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "Form 없음")
    })
    ResponseEntity<ApiResult<AddFormNoticeAdminResponse>> addFormNotice(
            @Parameter(description = "Form ID", example = "1") Long formId,
            @Parameter(description = "안내문 추가 요청") FormNoticeRequest request
    );

    @Operation(summary = "안내문 수정", description = "기존 안내문의 내용, 섹션 타입, 학과 설정을 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "규칙 위반/Form 불일치"),
            @ApiResponse(responseCode = "404", description = "FormNotice 없음")
    })
    ResponseEntity<ApiResult<Void>> updateFormNotice(
            @Parameter(description = "Form ID", example = "1") Long formId,
            @Parameter(description = "Notice ID", example = "5") Long noticeId,
            @Parameter(description = "안내문 수정 요청") FormNoticeRequest request
    );

    @Operation(summary = "안내문 삭제", description = "특정 안내문을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "Form 불일치"),
            @ApiResponse(responseCode = "404", description = "FormNotice 없음")
    })
    ResponseEntity<ApiResult<Void>> deleteFormNotice(
            @Parameter(description = "Form ID", example = "1") Long formId,
            @Parameter(description = "Notice ID", example = "5") Long noticeId
    );
}
