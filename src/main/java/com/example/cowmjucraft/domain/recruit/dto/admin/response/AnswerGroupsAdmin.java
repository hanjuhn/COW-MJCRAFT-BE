package com.example.cowmjucraft.domain.recruit.dto.admin.response;

import com.example.cowmjucraft.domain.recruit.entity.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnswerGroupsAdmin {

    private final List<AnswerItem> basic;
    private final List<AnswerItem> common;
    private final List<AnswerItem> firstDepartment;
    private final List<AnswerItem> secondDepartment;

    public AnswerGroupsAdmin(Application application, List<Answer> answers, Map<String, String> urlMap) {
        this.basic = new ArrayList<>();
        this.common = new ArrayList<>();
        this.firstDepartment = new ArrayList<>();
        this.secondDepartment = new ArrayList<>();

        DepartmentType first = application.getFirstDepartment();
        DepartmentType second = application.getSecondDepartment();

        for (Answer answer : answers) {
            FormQuestion formQuestion = answer.getFormQuestion();
            String key = answer.getValue();
            String fileUrl = null;

            if (formQuestion.getAnswerType() == AnswerType.FILE && key != null) {
                fileUrl = urlMap.get(key);
            }

            if (formQuestion.getSectionType() == SectionType.BASIC) {
                basic.add(new AnswerItem(formQuestion.getId(), answer.getValue(), fileUrl));
                continue;
            }

            if (formQuestion.getSectionType() == SectionType.COMMON) {
                common.add(new AnswerItem(formQuestion.getId(), answer.getValue(), fileUrl));
                continue;
            }

            if (formQuestion.getSectionType() == SectionType.DEPARTMENT) {
                DepartmentType dt = formQuestion.getDepartmentType();
                if (dt == first) {
                    firstDepartment.add(new AnswerItem(formQuestion.getId(), answer.getValue(), fileUrl));
                } else if (dt == second) {
                    secondDepartment.add(new AnswerItem(formQuestion.getId(), answer.getValue(), fileUrl));
                }
            }
        }
    }

    public List<AnswerItem> getCommon() {
        return common;
    }

    public List<AnswerItem> getBasic() {
        return basic;
    }

    public List<AnswerItem> getFirstDepartment() {
        return firstDepartment;
    }

    public List<AnswerItem> getSecondDepartment() {
        return secondDepartment;
    }
}
