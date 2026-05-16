package com.dbexport.service;

import com.dbexport.config.AppConfig;
import com.dbexport.model.Template;
import com.dbexport.util.FileUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class TemplateService {

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ObjectMapper objectMapper;

    private List<Template> templates = new ArrayList<>();
    private AtomicLong idGenerator = new AtomicLong(1);
    private String templatesFilePath;

    @PostConstruct
    public void init() {
        String templatePath = appConfig.getTemplatePath();
        FileUtil.ensureDirectoryExists(templatePath);
        templatesFilePath = templatePath + File.separator + "templates.json";
        loadTemplates();
    }

    private void loadTemplates() {
        File file = new File(templatesFilePath);
        if (!file.exists()) {
            return;
        }
        try {
            templates = objectMapper.readValue(file, new TypeReference<List<Template>>() {});
            if (templates != null && !templates.isEmpty()) {
                long maxId = templates.stream()
                        .mapToLong(Template::getId)
                        .max()
                        .orElse(0);
                idGenerator.set(maxId + 1);
            } else {
                templates = new ArrayList<>();
            }
        } catch (IOException e) {
            log.error("加载模板失败", e);
            templates = new ArrayList<>();
        }
    }

    private void saveTemplates() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(templatesFilePath), templates);
        } catch (IOException e) {
            log.error("保存模板失败", e);
        }
    }

    public Template save(Template template) {
        if (template.getId() == null) {
            template.setId(idGenerator.getAndIncrement());
            template.setCreateTime(System.currentTimeMillis());
            template.setUpdateTime(System.currentTimeMillis());
            templates.add(template);
        } else {
            Template existing = getById(template.getId());
            if (existing != null) {
                existing.setName(template.getName());
                existing.setDescription(template.getDescription());
                existing.setConfigJson(template.getConfigJson());
                existing.setUpdateTime(System.currentTimeMillis());
            }
        }
        saveTemplates();
        return template;
    }

    public List<Template> listAll() {
        return new ArrayList<>(templates);
    }

    public Template getById(Long id) {
        return templates.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public boolean deleteById(Long id) {
        boolean removed = templates.removeIf(t -> t.getId().equals(id));
        if (removed) {
            saveTemplates();
        }
        return removed;
    }

    public int deleteByIds(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            if (deleteById(id)) {
                count++;
            }
        }
        return count;
    }

    public Path exportTemplates(List<Long> ids) throws IOException {
        String tempPath = appConfig.getTempPath();
        FileUtil.ensureDirectoryExists(tempPath);
        
        List<Template> exportTemplates;
        if (ids == null || ids.isEmpty()) {
            exportTemplates = templates;
        } else {
            exportTemplates = new ArrayList<>();
            for (Long id : ids) {
                Template t = getById(id);
                if (t != null) {
                    exportTemplates.add(t);
                }
            }
        }

        String zipFileName = tempPath + File.separator + "templates_" + System.currentTimeMillis() + ".zip";
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(Paths.get(zipFileName)))) {
            for (Template template : exportTemplates) {
                String fileName = "template_" + template.getId() + ".json";
                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                zos.write(objectMapper.writeValueAsBytes(template));
                zos.closeEntry();
            }
        }

        return Paths.get(zipFileName);
    }

    public List<Template> importTemplates(Path zipFile) throws IOException {
        List<Template> importedTemplates = new ArrayList<>();
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".json")) {
                    try {
                        Template template = objectMapper.readValue(zis, Template.class);
                        if (template != null && template.getName() != null) {
                            template.setId(null);
                            Template saved = save(template);
                            importedTemplates.add(saved);
                        }
                    } catch (Exception e) {
                        log.warn("导入模板失败: {}", entry.getName(), e);
                    }
                }
                zis.closeEntry();
            }
        }

        return importedTemplates;
    }
}
