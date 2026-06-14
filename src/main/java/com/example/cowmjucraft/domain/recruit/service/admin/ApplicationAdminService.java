package com.example.cowmjucraft.domain.recruit.service.admin;

import com.example.cowmjucraft.domain.recruit.dto.admin.request.ApplicationResultUpdateAdminRequest;
import com.example.cowmjucraft.domain.recruit.dto.admin.response.AnswerGroupsAdmin;
import com.example.cowmjucraft.domain.recruit.dto.admin.response.ApplicationDetailAdminResponse;
import com.example.cowmjucraft.domain.recruit.dto.admin.response.ApplicationListAdminResponse;
import com.example.cowmjucraft.domain.recruit.entity.*;
import com.example.cowmjucraft.domain.recruit.exception.RecruitException;
import com.example.cowmjucraft.domain.recruit.repository.AnswerRepository;
import com.example.cowmjucraft.domain.recruit.repository.ApplicationRepository;
import com.example.cowmjucraft.domain.recruit.repository.FormRepository;
import com.example.cowmjucraft.domain.recruit.exception.RecruitErrorType;
import com.example.cowmjucraft.global.cloud.S3PresignFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ApplicationAdminService {

    private static final Set<String> APPLICANT_NAME_LABELS = Set.of("이름", "성명", "지원자 이름");
    private static final Pattern UUID_FILE_PREFIX = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}-"
    );

    private final FormRepository formRepository;
    private final ApplicationRepository applicationRepository;
    private final AnswerRepository answerRepository;
    private final S3PresignFacade s3PresignFacade;

    @Transactional(readOnly = true)
    public List<ApplicationListAdminResponse> getApplicationsByFormId(Long formId) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));

        List<Application> apps = applicationRepository.findAllByForm(form);
        List<Answer> answers = apps.isEmpty()
                ? List.of()
                : answerRepository.findAllByApplicationInFetchFormQuestion(apps);
        Map<Long, String> applicantNames = extractApplicantNames(answers);

        List<ApplicationListAdminResponse> result = new ArrayList<>();

        for (Application application : apps) {
            result.add(new ApplicationListAdminResponse(
                    application.getId(),
                    application.getStudentId(),
                    applicantNames.get(application.getId()),
                    application.getFirstDepartment(),
                    application.getSecondDepartment(),
                    application.getResultStatus(),
                    application.getCreatedAt(),
                    application.getUpdatedAt()
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ApplicationDetailAdminResponse getApplication(Long formId, Long applicationId) {

        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.FORM_NOT_FOUND));

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.APPLICATION_NOT_FOUND));

        if (!application.getForm().getId().equals(form.getId())) {
            throw new RecruitException(RecruitErrorType.APPLICATION_NOT_IN_THIS_FORM);
        }

        List<Answer> answers = answerRepository.findAllByApplication(application);

        List<String> fileKeys = answers.stream().filter(answer -> answer.getFormQuestion().getAnswerType() == AnswerType.FILE)
                .map(Answer::getValue).filter(Objects::nonNull).distinct().toList();

        FileLinkMaps fileLinkMaps = createFileLinkMaps(fileKeys);
        AnswerGroupsAdmin groups = new AnswerGroupsAdmin(
                application,
                answers,
                fileLinkMaps.previewUrlMap(),
                fileLinkMaps.downloadUrlMap(),
                fileLinkMaps.fileNameMap()
        );

        return new ApplicationDetailAdminResponse(
                application.getId(),
                application.getStudentId(),
                extractApplicantNames(answers).get(application.getId()),
                application.getFirstDepartment(),
                application.getSecondDepartment(),
                application.getResultStatus(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                groups.getBasic(),
                groups.getCommon(),
                groups.getFirstDepartment(),
                groups.getSecondDepartment()
        );
    }

    @Transactional
    public void deleteApplication(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.APPLICATION_NOT_FOUND));

        answerRepository.deleteAllByApplication(application);
        applicationRepository.delete(application);
    }

    @Transactional
    public void updateResult(Long applicationId, ApplicationResultUpdateAdminRequest request) {

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RecruitException(RecruitErrorType.APPLICATION_NOT_FOUND));

        application.setResultStatus(request.getResultStatus());
    }

    private Map<Long, String> extractApplicantNames(List<Answer> answers) {
        return answers.stream()
                .filter(answer -> isApplicantNameQuestion(answer.getFormQuestion()))
                .filter(answer -> answer.getValue() != null && !answer.getValue().isBlank())
                .collect(Collectors.toMap(
                        answer -> answer.getApplication().getId(),
                        answer -> answer.getValue().trim(),
                        (first, ignored) -> first
                ));
    }

    private boolean isApplicantNameQuestion(FormQuestion formQuestion) {
        if (formQuestion == null || formQuestion.getQuestion() == null) {
            return false;
        }
        String label = normalizeQuestionLabel(formQuestion.getQuestion().getLabel());
        return APPLICANT_NAME_LABELS.contains(label);
    }

    private String normalizeQuestionLabel(String label) {
        if (label == null) {
            return "";
        }
        return label.trim().replace("*", "").trim();
    }

    private FileLinkMaps createFileLinkMaps(List<String> fileKeys) {
        if (fileKeys.isEmpty()) {
            return new FileLinkMaps(Map.of(), Map.of(), Map.of());
        }

        Map<String, String> previewUrlMap = new HashMap<>();
        Map<String, String> downloadUrlMap = new HashMap<>();
        Map<String, String> fileNameMap = new HashMap<>();

        for (String key : fileKeys) {
            String fileName = extractOriginalFileName(key);
            String contentType = inferContentType(fileName);

            previewUrlMap.put(key, s3PresignFacade.presignGet(key, fileName, contentType, false));
            downloadUrlMap.put(key, s3PresignFacade.presignGet(key, fileName, contentType, true));
            fileNameMap.put(key, fileName);
        }

        return new FileLinkMaps(previewUrlMap, downloadUrlMap, fileNameMap);
    }

    private String extractOriginalFileName(String key) {
        String trimmed = key == null ? "" : key.trim();
        int lastSlash = trimmed.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
        String withoutUuid = UUID_FILE_PREFIX.matcher(fileName).replaceFirst("");
        return withoutUuid.isBlank() ? "download" : withoutUuid;
    }

    private String inferContentType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    private record FileLinkMaps(
            Map<String, String> previewUrlMap,
            Map<String, String> downloadUrlMap,
            Map<String, String> fileNameMap
    ) {
    }
}
