package com.example.cowmjucraft.domain.recruit.repository;

import com.example.cowmjucraft.domain.recruit.entity.Answer;
import com.example.cowmjucraft.domain.recruit.entity.Application;
import com.example.cowmjucraft.domain.recruit.entity.FormQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    @Query("select a from Answer a join fetch a.formQuestion where a.application = :application")
    List<Answer> findAllByApplicationFetchFormQuestion(@Param("application") Application application);

    @Query("select a from Answer a join fetch a.formQuestion fq join fetch fq.question where a.application in :applications")
    List<Answer> findAllByApplicationInFetchFormQuestion(@Param("applications") List<Application> applications);

    List<Answer> findAllByApplication(Application application);

    void deleteAllByApplication(Application application);

    Optional<Answer> findByApplicationAndFormQuestion(Application application, FormQuestion formQuestion);
}
