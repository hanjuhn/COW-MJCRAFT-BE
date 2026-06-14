package com.example.cowmjucraft.domain.recruit.service.admin;

import com.example.cowmjucraft.domain.recruit.dto.admin.request.*;
import com.example.cowmjucraft.domain.recruit.dto.admin.response.*;
import com.example.cowmjucraft.domain.recruit.entity.*;
import com.example.cowmjucraft.domain.recruit.exception.RecruitException;
import com.example.cowmjucraft.domain.recruit.repository.*;
import com.example.cowmjucraft.domain.recruit.exception.RecruitErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class FormAdminService {

    private final FormRepository formRepository;
    private final QuestionRepository questionRepository;
    private final FormQuestionRepository formQuestionRepository;
    private final FormNoticeRepository formNoticeRepository;
    private final ApplicationRepository applicationRepository;

    @Transactional
    public FormCreateAdminResponse createForm(FormCreateAdminRequest request) {

        if (request.isOpen()) {
            Form openForm = formRepository.findFirstByOpenTrue();
            if (openForm != null) {
                openForm.close();
                formRepository.save(openForm);
            }
        }

        Form form = new Form(request.getTitle(), request.isOpen());
        formRepository.save(form);

        return new FormCreateAdminResponse(form.getId(), form.isOpen());
    }

    @Transactional
    public void deleteForm(Long formId) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));

        if (form.isOpen()) {
            throw new RecruitException(RecruitErrorType.CANNOT_DELETE_OPEN_FORM);
        }

        if (applicationRepository.existsByForm(form)) {
            throw new RecruitException(RecruitErrorType.CANNOT_DELETE_FORM_WITH_APPLICATIONS);
        }

        List<Long> questionIds = formQuestionRepository.findQuestionIdsByFormId(formId);

        formQuestionRepository.deleteAllByFormId(formId);

        formNoticeRepository.deleteAllByFormId(formId);

        if (!questionIds.isEmpty()) {
            questionRepository.deleteAllByIdInBatch(questionIds);
        }

        formRepository.delete(form);
    }

    @Transactional
    public void openForm(Long formId) {
        Form target = formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));

        Form openForm = formRepository.findFirstByOpenTrue();
        if (openForm != null && !openForm.getId().equals(target.getId())) {
            openForm.close();
            formRepository.save(openForm);
        }

        target.open();
        formRepository.save(target);
    }

    @Transactional
    public void closeForm(Long formId) {
        Form target = formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));
        target.close();
        formRepository.save(target);
    }

    @Transactional
    public AddQuestionAdminResponse addQuestion(Long formId, AddQuestionAdminRequest request) {

        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));

        if (formQuestionRepository.existsByFormAndQuestionOrder(form, request.getQuestionOrder())) {
            throw new RecruitException(RecruitErrorType.DUPLICATE_QUESTION_ORDER);
        }

        validateSectionDepartment(request.getSectionType(), request.getDepartmentType());

        if (request.getAnswerType() != AnswerType.SELECT && request.getSelectOptions() != null) {
            throw new RecruitException(RecruitErrorType.SELECT_OPTIONS_ONLY_FOR_SELECT);
        }

        Question question = new Question(request.getLabel(), request.getDescription());
        questionRepository.save(question);

        FormQuestion formQuestion = FormQuestion.builder().form(form)
                .question(question).questionOrder(request.getQuestionOrder())
                .answerType(request.getAnswerType()).required(request.isRequired()).sectionType(request.getSectionType())
                .departmentType(request.getDepartmentType()).selectOptions(request.getSelectOptions()).build();

        formQuestionRepository.save(formQuestion);

        return new AddQuestionAdminResponse(question.getId(), formQuestion.getId());
    }

    @Transactional(readOnly = true)
    public List<FormListAdminResponse> getForms() {

        List<Form> forms = formRepository.findAllByOrderByIdDesc();
        List<FormListAdminResponse> result = new ArrayList<>();

        for (Form form : forms) {
            result.add(
                    new FormListAdminResponse(
                            form.getId(),
                            form.getTitle(),
                            form.isOpen()
                    )
            );
        }

        return result;
    }

    @Transactional(readOnly = true)
    public FormDetailAdminResponse getForm(Long formId) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));

        List<FormNotice> notices = formNoticeRepository.findAllByForm(form);

        List<FormQuestion> questions = formQuestionRepository.findAllByFormOrderByQuestionOrderAsc(form);

        return new FormDetailAdminResponse(
                form.getId(),
                form.getTitle(),
                form.isOpen(),
                notices.stream().map(FormDetailAdminResponse.NoticeResponse::from).toList(),
                questions.stream().map(FormDetailAdminResponse.QuestionResponse::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<FormQuestionListAdminResponse> getFormQuestions(Long formId) {

        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));

        List<FormQuestion> formQuestions =
                formQuestionRepository.findAllByFormOrderByQuestionOrderAsc(form);

        List<FormQuestionListAdminResponse> result = new ArrayList<>();

        for (FormQuestion formQuestion : formQuestions) {
            Question question = formQuestion.getQuestion();

            result.add(new FormQuestionListAdminResponse(
                    formQuestion.getId(),
                    question.getId(),
                    question.getLabel(),
                    question.getDescription(),
                    formQuestion.getQuestionOrder(),
                    formQuestion.isRequired(),
                    formQuestion.getAnswerType(),
                    formQuestion.getSelectOptions(),
                    formQuestion.getSectionType(),
                    formQuestion.getDepartmentType()
            ));
        }

        return result;
    }

    @Transactional
    public void deleteFormQuestion(Long formId, Long formQuestionId) {

        FormQuestion formQuestion = formQuestionRepository.findById(formQuestionId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_QUESTION_NOT_FOUND));

        if (!formQuestion.getForm().getId().equals(formId)) {
            throw new RecruitException(RecruitErrorType.FORM_QUESTION_NOT_IN_THIS_FORM);
        }

        formQuestionRepository.delete(formQuestion);
    }

    @Transactional
    public void updateFormQuestion(Long formId, Long formQuestionId, FormQuestionUpdateAdminRequest request) {

        FormQuestion formQuestion = formQuestionRepository.findById(formQuestionId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_QUESTION_NOT_FOUND));

        if (!formQuestion.getForm().getId().equals(formId)) {
            throw new RecruitException(RecruitErrorType.FORM_QUESTION_NOT_IN_THIS_FORM);
        }

        validateSectionDepartment(request.getSectionType(), request.getDepartmentType());
        if (request.getAnswerType() != AnswerType.SELECT && request.getSelectOptions() != null) {
            throw new RecruitException(RecruitErrorType.SELECT_OPTIONS_ONLY_FOR_SELECT);
        }

        Question question = formQuestion.getQuestion();
        question.update(request.getLabel(), request.getDescription());

        formQuestion.update(
                request.getQuestionOrder(),
                request.isRequired(),
                request.getAnswerType(),
                request.getSelectOptions(),
                request.getSectionType(),
                request.getDepartmentType()
        );
    }

    @Transactional
    public FormCopyAdminResponse copyFormQuestionsOverwrite(Long targetFormId, FormCopyAdminRequest request) {

        Long sourceFormId = request.getSourceFormId();
        if (sourceFormId == null) {
            throw new RecruitException(RecruitErrorType.SOURCE_FORM_ID_REQUIRED);
        }
        if (sourceFormId.equals(targetFormId)) {
            throw new RecruitException(RecruitErrorType.SOURCE_AND_TARGET_CANNOT_BE_SAME);
        }

        Form targetForm = formRepository.findById(targetFormId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.TARGET_FORM_NOT_FOUND));

        Form sourceForm = formRepository.findById(sourceFormId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.SOURCE_FORM_NOT_FOUND));

        formQuestionRepository.deleteAllByForm(targetForm);
        formNoticeRepository.deleteAllByForm(targetForm);

        List<FormQuestion> sourceQuestions =
                formQuestionRepository.findAllByFormOrderByQuestionOrderAsc(sourceForm);

        int copied = 0;

        for (FormQuestion formQuestion : sourceQuestions) {
            Question srcQ = formQuestion.getQuestion();

            Question newQuestion = new Question(srcQ.getLabel(), srcQ.getDescription());
            questionRepository.save(newQuestion);

            FormQuestion newFormQuestion = FormQuestion.builder().form(targetForm).question(newQuestion)
                    .questionOrder(formQuestion.getQuestionOrder()).answerType(formQuestion.getAnswerType())
                    .required(formQuestion.isRequired()).sectionType(formQuestion.getSectionType())
                    .departmentType(formQuestion.getDepartmentType()).selectOptions(formQuestion.getSelectOptions()).build();

            formQuestionRepository.save(newFormQuestion);

            copied++;
        }

        List<FormNotice> sourceNotices = formNoticeRepository.findAllByForm(sourceForm);
        for (FormNotice notice : sourceNotices) {
            FormNotice newNotice = FormNotice.builder()
                    .form(targetForm)
                    .sectionType(notice.getSectionType())
                    .departmentType(notice.getDepartmentType())
                    .title(notice.getTitle())
                    .content(notice.getContent())
                    .build();

            formNoticeRepository.save(newNotice);
        }

        return new FormCopyAdminResponse(targetFormId, sourceFormId, copied);
    }

    @Transactional
    public AddFormNoticeAdminResponse addFormNotice(Long formId, FormNoticeRequest request) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));

        validateNoticeRequest(request);

        FormNotice notice = FormNotice.builder()
                .form(form)
                .sectionType(request.getSectionType())
                .departmentType(request.getDepartmentType())
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        formNoticeRepository.save(notice);
        return new AddFormNoticeAdminResponse(notice.getId());
    }

    @Transactional
    public void updateFormNotice(Long formId, Long noticeId, FormNoticeRequest request) {
        FormNotice notice = formNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.NOTICE_NOT_FOUND));

        if (!notice.getForm().getId().equals(formId)) {
            throw new RecruitException(RecruitErrorType.NOTICE_NOT_IN_THIS_FORM);
        }

        validateNoticeRequest(request);

        notice.update(
                request.getSectionType(),
                request.getDepartmentType(),
                request.getTitle(),
                request.getContent()
        );
    }

    @Transactional
    public void deleteFormNotice(Long formId, Long noticeId) {
        FormNotice notice = formNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.NOTICE_NOT_FOUND));

        if (!notice.getForm().getId().equals(formId)) {
            throw new RecruitException(RecruitErrorType.NOTICE_NOT_IN_THIS_FORM);
        }

        formNoticeRepository.delete(notice);
    }

    private void validateNoticeRequest(FormNoticeRequest request) {
        validateSectionDepartment(request.getSectionType(), request.getDepartmentType());
    }

    private void validateSectionDepartment(SectionType sectionType, DepartmentType departmentType) {
        if (sectionType == null) {
            throw new RecruitException(RecruitErrorType.INVALID_SECTION_OR_DEPARTMENT_TYPE);
        }
        if (sectionType == SectionType.DEPARTMENT && departmentType == null) {
            throw new RecruitException(RecruitErrorType.DEPARTMENT_TYPE_REQUIRED_FOR_DEPARTMENT_SECTION);
        }
        if (sectionType != SectionType.DEPARTMENT && departmentType != null) {
            throw new RecruitException(RecruitErrorType.COMMON_SECTION_CANNOT_HAVE_DEPARTMENT);
        }
    }
}
