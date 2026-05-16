package com.dbexport.service;

import com.dbexport.model.Template;

import java.nio.file.Path;
import java.util.List;

public interface TemplateService {

    Template save(Template template);

    List<Template> listAll();

    Template getById(Long id);

    boolean deleteById(Long id);

    int deleteByIds(List<Long> ids);

    Path exportTemplates(List<Long> ids);

    List<Template> importTemplates(Path zipFile);

    List<Template> search(String keyword);
}
