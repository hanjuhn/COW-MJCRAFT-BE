package com.example.cowmjucraft.domain.recruit.service.client;

import com.example.cowmjucraft.domain.recruit.dto.client.request.ApplicationCreateRequest;
import com.example.cowmjucraft.domain.recruit.dto.client.request.ApplicationReadRequest;
import com.example.cowmjucraft.domain.recruit.dto.client.request.ApplicationUpdateRequest;
import com.example.cowmjucraft.domain.recruit.dto.client.request.ResultReadRequest;
import com.example.cowmjucraft.domain.recruit.dto.client.response.*;
import com.example.cowmjucraft.domain.recruit.entity.*;
import com.example.cowmjucraft.domain.recruit.exception.RecruitException;
import com.example.cowmjucraft.domain.recruit.repository.*;
import com.example.cowmjucraft.global.cloud.S3PresignFacade;
import com.example.cowmjucraft.domain.recruit.exception.RecruitErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ApplicationService {

    private final FormRepository formRepository;
    private final ApplicationRepository applicationRepository;
    private final FormQuestionRepository formQuestionRepository;
    private final AnswerRepository answerRepository;
    private final PasswordEncoder passwordEncoder;
    private final QuestionRepository questionRepository;
    private final S3PresignFacade s3PresignFacade;
    private final FormNoticeRepository formNoticeRepository;

    @Transactional
    public ApplicationCreateResponse create(ApplicationCreateRequest request) {

        Form form = formRepository.findFirstByOpenTrue();
        if (form == null || !form.isOpen()) {
            throw new RecruitException(RecruitErrorType.RECRUITMENT_CLOSED);
        }

        if (applicationRepository.existsByFormAndStudentId(form, request.getStudentId())) {
            throw new RecruitException(RecruitErrorType.DUPLICATE_STUDENT_ID);
        }

        if (request.getFirstDepartment() == request.getSecondDepartment()) {
            throw new RecruitException(RecruitErrorType.FIRST_SECOND_DEPARTMENT_MUST_BE_DIFFERENT);
        }

        DepartmentType firstDepartment = request.getFirstDepartment();
        DepartmentType secondDepartment = request.getSecondDepartment();

        List<FormQuestion> formQuestions = formQuestionRepository.findAllByForm(form);
        if (formQuestions == null || formQuestions.isEmpty()) {
            throw new RecruitException(RecruitErrorType.FORM_QUESTION_NOT_FOUND);
        }

        Map<Long, FormQuestion> formQuestionMap = new HashMap<>();
        Set<Long> requiredAlwaysIds = new HashSet<>();
        Set<Long> requiredDeptIds = new HashSet<>();

        for (FormQuestion formQuestion : formQuestions) {
            formQuestionMap.put(formQuestion.getId(), formQuestion);

            if (formQuestion.isRequired()) {
                if (isAlwaysVisibleSection(formQuestion.getSectionType())) {
                    requiredAlwaysIds.add(formQuestion.getId());
                } else if (formQuestion.getSectionType() == SectionType.DEPARTMENT) {
                    if (formQuestion.getDepartmentType() == firstDepartment || formQuestion.getDepartmentType() == secondDepartment) {
                        requiredDeptIds.add(formQuestion.getId());
                    }
                }
            }
        }

        List<ApplicationCreateRequest.AnswerItemRequest> requestAnswers = (request.getAnswers() == null) ? List.of() : request.getAnswers();

        Map<Long, String> answerValueMap = new HashMap<>();

        for (ApplicationCreateRequest.AnswerItemRequest answer : requestAnswers) {
            Long formQuestionId = answer.getFormQuestionId();
            if (formQuestionId == null) {
                throw new RecruitException(RecruitErrorType.FORM_QUESTION_ID_REQUIRED);
            }

            if (!formQuestionMap.containsKey(formQuestionId)) {
                throw new RecruitException(RecruitErrorType.FORM_QUESTION_NOT_FOUND);
            }

            if (answerValueMap.containsKey(formQuestionId)) {
                throw new RecruitException(RecruitErrorType.DUPLICATE_ANSWER);
            }

            String value = answer.getValue();
            if (value != null && value.isBlank()) {
                value = null;
            }

            answerValueMap.put(formQuestionId, value);
        }

        for (Long requiredId : requiredAlwaysIds) {
            String value = answerValueMap.get(requiredId);
            if (value == null) {
                throw new RecruitException(RecruitErrorType.REQUIRED_ANSWER_MISSING);
            }
        }

        for (Long requiredId : requiredDeptIds) {
            String value = answerValueMap.get(requiredId);
            if (value == null) {
                throw new RecruitException(RecruitErrorType.REQUIRED_ANSWER_MISSING);
            }
        }

        for (Map.Entry<Long, String> e : answerValueMap.entrySet()) {
            Long formQuestionId = e.getKey();
            FormQuestion formQuestion = formQuestionMap.get(formQuestionId);

            if (formQuestion.getSectionType() == SectionType.DEPARTMENT) {
                DepartmentType departmentType = formQuestion.getDepartmentType();
                if (departmentType != firstDepartment && departmentType != secondDepartment) {
                    throw new RecruitException(RecruitErrorType.ANSWER_FOR_UNSELECTED_DEPARTMENT);
                }
            }

            if (formQuestion.isRequired() && e.getValue() == null) {
                throw new RecruitException(RecruitErrorType.REQUIRED_ANSWER_MISSING);
            }
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        Application application = new Application(
                form,
                request.getStudentId(),
                passwordHash,
                firstDepartment,
                secondDepartment
        );
        applicationRepository.save(application);

        for (Map.Entry<Long, String> e : answerValueMap.entrySet()) {
            String value = e.getValue();
            if (value == null) {
                continue;
            }
            FormQuestion formQuestion = formQuestionMap.get(e.getKey());
            Answer answer = new Answer(application, formQuestion, value);
            answerRepository.save(answer);
        }

        return new ApplicationCreateResponse(application.getId());
    }

    @Transactional(readOnly = true)
    public ApplicationReadResponse read(ApplicationReadRequest request) {

        Form form = findReadableForm(request.getFormId());

        Application application = applicationRepository.findByFormAndStudentId(form, request.getStudentId())
                .orElseThrow(() -> new RecruitException(RecruitErrorType.APPLICATION_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), application.getPasswordHash())) {
            throw new RecruitException(RecruitErrorType.INVALID_CREDENTIALS);
        }

        boolean editable = form.isOpen();

        List<Answer> answers = answerRepository.findAllByApplication(application);

        List<String> fileKeys = answers.stream()
                .filter(a -> a.getFormQuestion().getAnswerType() == AnswerType.FILE)
                .map(Answer::getValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, String> urlMap = fileKeys.isEmpty() ? Map.of() : s3PresignFacade.presignGet(fileKeys);

        List<ApplicationReadResponse.AnswerItem> basic = new ArrayList<>();
        List<ApplicationReadResponse.AnswerItem> common = new ArrayList<>();
        List<ApplicationReadResponse.AnswerItem> firstDepartment = new ArrayList<>();
        List<ApplicationReadResponse.AnswerItem> secondDepartment = new ArrayList<>();

        for (Answer answer : answers) {
            FormQuestion formQuestion = answer.getFormQuestion();
            String value = answer.getValue();

            if (formQuestion.getSectionType() == SectionType.BASIC) {
                basic.add(toAnswerItem(formQuestion, value, urlMap));
                continue;
            }

            if (formQuestion.getSectionType() == SectionType.COMMON) {
                common.add(toAnswerItem(formQuestion, value, urlMap));
                continue;
            }

            if (formQuestion.getSectionType() == SectionType.DEPARTMENT) {
                DepartmentType departmentType = formQuestion.getDepartmentType();

                if (departmentType == application.getFirstDepartment()) {
                    firstDepartment.add(toAnswerItem(formQuestion, value, urlMap));
                } else if (departmentType == application.getSecondDepartment()) {
                    secondDepartment.add(toAnswerItem(formQuestion, value, urlMap));
                } else {
                    throw new RecruitException(RecruitErrorType.INVALID_SECTION_OR_DEPARTMENT_TYPE);
                }
            }
        }

        List<FormNotice> notices = formNoticeRepository.findAllByForm(form);
        List<FormQuestion> formQuestions = formQuestionRepository.findAllByFormOrderByQuestionOrderAsc(form);

        ApplicationReadResponse.NoticeItem basicNotice = null;
        ApplicationReadResponse.NoticeItem commonNotice = null;
        ApplicationReadResponse.NoticeItem firstDepartmentNotice = null;
        ApplicationReadResponse.NoticeItem secondDepartmentNotice = null;

        for (FormNotice notice : notices) {
            ApplicationReadResponse.NoticeItem item =
                    new ApplicationReadResponse.NoticeItem(notice.getTitle(), notice.getContent());

            if (notice.getSectionType() == SectionType.BASIC) {
                basicNotice = item;
            } else if (notice.getSectionType() == SectionType.COMMON) {
                commonNotice = item;
            } else if (notice.getSectionType() == SectionType.DEPARTMENT) {
                if (notice.getDepartmentType() == application.getFirstDepartment()) {
                    firstDepartmentNotice = item;
                } else if (notice.getDepartmentType() == application.getSecondDepartment()) {
                    secondDepartmentNotice = item;
                }
            }
        }

        return new ApplicationReadResponse(
                editable,
                application.getId(),
                application.getStudentId(),
                application.getFirstDepartment(),
                application.getSecondDepartment(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                basicNotice,
                commonNotice,
                firstDepartmentNotice,
                secondDepartmentNotice,
                basic,
                common,
                firstDepartment,
                secondDepartment,
                notices.stream().map(ApplicationFormInfoResponse.NoticeDto::from).toList(),
                formQuestions.stream().map(ApplicationFormInfoResponse.QuestionDto::from).toList()
        );
    }

    @Transactional
    public ApplicationUpdateResponse update(ApplicationUpdateRequest request) {

        Form form = findWritableForm(request.getFormId());

        Application application = applicationRepository.findByFormAndStudentId(form, request.getStudentId())
                .orElseThrow(() -> new RecruitException(RecruitErrorType.APPLICATION_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), application.getPasswordHash())) {
            throw new RecruitException(RecruitErrorType.INVALID_CREDENTIALS);
        }

        DepartmentType firstDepartment = request.getFirstDepartment();
        DepartmentType secondDepartment = request.getSecondDepartment();

        if (firstDepartment != null || secondDepartment != null) {
            if (firstDepartment == null || secondDepartment == null) {
                throw new RecruitException(RecruitErrorType.BOTH_DEPARTMENTS_REQUIRED);
            }
            if (firstDepartment == secondDepartment) {
                throw new RecruitException(RecruitErrorType.FIRST_SECOND_DEPARTMENT_MUST_BE_DIFFERENT);
            }
            application.changeDepartments(firstDepartment, secondDepartment);
        }

        if (request.getAnswers() != null) {

            List<ApplicationUpdateRequest.AnswerItemRequest> requestAnswers = request.getAnswers();

            Set<Long> seen = new HashSet<>();
            List<Long> formIds = new ArrayList<>();

            for (ApplicationUpdateRequest.AnswerItemRequest answer : requestAnswers) {
                Long id = answer.getFormQuestionId();
                if (id == null) {
                    throw new RecruitException(RecruitErrorType.FORM_QUESTION_ID_REQUIRED);
                }
                if (!seen.add(id)) {
                    throw new RecruitException(RecruitErrorType.DUPLICATE_ANSWER);
                }
                formIds.add(id);
            }

            List<FormQuestion> fetched = formQuestionRepository.findAllByIdInAndForm_Id(formIds, form.getId());
            if (fetched.size() != formIds.size()) {
                throw new RecruitException(RecruitErrorType.FORM_QUESTION_NOT_IN_THIS_FORM);
            }

            Map<Long, FormQuestion> formQuestionMap = new HashMap<>();
            for (FormQuestion formQuestion : fetched) {
                formQuestionMap.put(formQuestion.getId(), formQuestion);
            }

            List<Answer> existingAnswers = answerRepository.findAllByApplicationFetchFormQuestion(application);
            Map<Long, Answer> answerMap = new HashMap<>();
            for (Answer answer : existingAnswers) {
                answerMap.put(answer.getFormQuestion().getId(), answer);
            }

            DepartmentType currentFirst = application.getFirstDepartment();
            DepartmentType currentSecond = application.getSecondDepartment();

            for (ApplicationUpdateRequest.AnswerItemRequest a : requestAnswers) {

                FormQuestion formQuestion = formQuestionMap.get(a.getFormQuestionId());
                if (formQuestion == null) {
                    throw new RecruitException(RecruitErrorType.FORM_QUESTION_NOT_FOUND);
                }

                if (formQuestion.getSectionType() == SectionType.DEPARTMENT) {
                    DepartmentType dt = formQuestion.getDepartmentType();
                    if (dt != currentFirst && dt != currentSecond) {
                        throw new RecruitException(RecruitErrorType.ANSWER_FOR_UNSELECTED_DEPARTMENT);
                    }
                }

                String value = a.getValue();
                if (value != null && value.isBlank()) {
                    value = null;
                }

                Answer existing = answerMap.get(formQuestion.getId());
                if (formQuestion.getAnswerType() == AnswerType.FILE && value != null && isHttpUrl(value) && existing != null) {
                    value = existing.getValue();
                }

                if (value == null) {
                    if (formQuestion.isRequired()) {
                        throw new RecruitException(RecruitErrorType.REQUIRED_ANSWER_CANNOT_BE_DELETED);
                    }
                    if (existing != null) {
                        if (formQuestion.getAnswerType() == AnswerType.FILE) {
                            s3PresignFacade.deleteByKeys(List.of(existing.getValue()));
                        }
                        answerRepository.delete(existing);
                    }
                    continue;
                }

                if (existing != null) {
                    if (formQuestion.getAnswerType() == AnswerType.FILE && !existing.getValue().equals(value)) {
                        s3PresignFacade.deleteByKeys(List.of(existing.getValue()));
                    }
                    existing.updateValue(value);
                } else {
                    answerRepository.save(new Answer(application, formQuestion, value));
                }
            }
        }

        return new ApplicationUpdateResponse(application.getId(), application.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public ResultReadResponse readResult(ResultReadRequest request) {

        Form form = findReadableForm(request.getFormId());

        Application application = applicationRepository.findByFormAndStudentId(form, request.getStudentId())
                .orElseThrow(() -> new RecruitException(RecruitErrorType.APPLICATION_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), application.getPasswordHash())) {
            throw new RecruitException(RecruitErrorType.INVALID_CREDENTIALS);
        }

        return new ResultReadResponse(application.getResultStatus());
    }

    @Transactional(readOnly = true)
    public ApplicationFormInfoResponse getOpenFormInfo() {
        Form form = formRepository.findFirstByOpenTrue();

        if (form == null) {
            throw new RecruitException(RecruitErrorType.FORM_NOT_FOUND);
        }

        List<FormNotice> notices = formNoticeRepository.findAllByForm(form);

        List<FormQuestion> questions = formQuestionRepository.findAllByFormOrderByQuestionOrderAsc(form);

        return ApplicationFormInfoResponse.from(form, notices, questions);
    }

    public S3PresignFacade.PresignPutBatchResult createAnswerFilePresignPut(List<S3PresignFacade.PresignPutFile> files) {
        return s3PresignFacade.createPresignPutBatch("uploads/recruit/answers", files);
    }

    private boolean isAlwaysVisibleSection(SectionType sectionType) {
        return sectionType == SectionType.BASIC || sectionType == SectionType.COMMON;
    }

    private Form findReadableForm(Long formId) {
        if (formId != null) {
            return formRepository.findById(formId)
                    .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));
        }

        Form form = formRepository.findFirstByOpenTrue();
        if (form == null) {
            form = formRepository.findTopByOrderByIdDesc();
        }
        if (form == null) {
            throw new RecruitException(RecruitErrorType.FORM_NOT_FOUND);
        }
        return form;
    }

    private Form findWritableForm(Long formId) {
        Form form = formId != null
                ? formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND))
                : formRepository.findFirstByOpenTrue();

        if (form == null || !form.isOpen()) {
            throw new RecruitException(RecruitErrorType.RECRUITMENT_CLOSED);
        }
        return form;
    }

    private ApplicationReadResponse.AnswerItem toAnswerItem(
            FormQuestion formQuestion,
            String value,
            Map<String, String> fileUrlMap
    ) {
        if (formQuestion.getAnswerType() != AnswerType.FILE || value == null) {
            return new ApplicationReadResponse.AnswerItem(formQuestion.getId(), value);
        }

        return new ApplicationReadResponse.AnswerItem(
                formQuestion.getId(),
                value,
                value,
                fileUrlMap.getOrDefault(value, value),
                extractOriginalFileName(value)
        );
    }

    private String extractOriginalFileName(String key) {
        String trimmed = key == null ? "" : key.trim();
        int lastSlash = trimmed.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
        return fileName.replaceFirst("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}-", "");
    }

    private boolean isHttpUrl(String value) {
        String trimmed = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }
}
