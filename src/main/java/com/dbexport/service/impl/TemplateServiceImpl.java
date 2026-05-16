package com.dbexport.service.impl;

import com.dbexport.model.Template;
import com.dbexport.service.TemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class TemplateServiceImpl implements TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Template> templateStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Value("${template.storage.path:./templates}")
    private String storagePath;

    @PostConstruct
    public void init() {
        try {
            Path dir = Paths.get(storagePath);
            Files.createDirectories(dir);
            Path dataFile = dir.resolve("templates.json");
            if (Files.exists(dataFile)) {
                String content = new String(Files.readAllBytes(dataFile), "UTF-8");
                Template[] templates = objectMapper.readValue(content, Template[].class);
                for (Template t : templates) {
                    templateStore.put(t.getId(), t);
                    if (t.getId() >= idGenerator.get()) {
                        idGenerator.set(t.getId() + 1);
                    }
                }
                log.info("加载了 {} 个模板", templates.length);
            }
        } catch (Exception e) {
            log.error("加载模板失败", e);
        }
    }

    @Override
    public Template save(Template template) {
        long now = System.currentTimeMillis();
        if (template.getId() == null) {
            template.setId(idGenerator.getAndIncrement());
            template.setCreateTime(now);
        }
        template.setUpdateTime(now);
        templateStore.put(template.getId(), template);
        persist();
        return template;
    }

    @Override
    public List<Template> listAll() {
        return new ArrayList<>(templateStore.values());
    }

    @Override
    public Template getById(Long id) {
        return templateStore.get(id);
    }

    @Override
    public boolean deleteById(Long id) {
        Template removed = templateStore.remove(id);
        if (removed != null) {
            persist();
            return true;
        }
        return false;
    }

    @Override
    public int deleteByIds(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            if (templateStore.remove(id) != null) count++;
        }
        if (count > 0) persist();
        return count;
    }

    @Override
    public Path exportTemplates(List<Long> ids) {
        try {
            Path tempFile = Files.createTempFile("templates_", ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                for (Long id : ids) {
                    Template t = templateStore.get(id);
                    if (t != null) {
                        String json = objectMapper.writeValueAsString(t);
                        zos.putNextEntry(new ZipEntry("template_" + t.getId() + ".json"));
                        zos.write(json.getBytes("UTF-8"));
                        zos.closeEntry();
                    }
                }
            }
            return tempFile;
        } catch (Exception e) {
            throw new RuntimeException("导出模板失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Template> importTemplates(Path zipFile) {
        List<Template> imported = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    log.warn("跳过可疑ZIP条目: {}", entryName);
                    continue;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                String json = baos.toString("UTF-8");
                Template t = objectMapper.readValue(json, Template.class);
                t.setId(null);
                t.setCreateTime(System.currentTimeMillis());
                t.setUpdateTime(System.currentTimeMillis());
                t = save(t);
                imported.add(t);
            }
        } catch (Exception e) {
            throw new RuntimeException("导入模板失败: " + e.getMessage(), e);
        }
        return imported;
    }

    @Override
    public List<Template> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return listAll();
        }
        String kw = keyword.trim().toLowerCase();
        List<Template> result = new ArrayList<>();
        for (Template t : templateStore.values()) {
            if ((t.getName() != null && t.getName().toLowerCase().contains(kw)) ||
                    (t.getDescription() != null && t.getDescription().toLowerCase().contains(kw))) {
                result.add(t);
            }
        }
        return result;
    }

    private synchronized void persist() {
        try {
            Path dir = Paths.get(storagePath);
            Files.createDirectories(dir);
            Path dataFile = dir.resolve("templates.json");
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new ArrayList<>(templateStore.values()));
            Files.write(dataFile, json.getBytes("UTF-8"));
        } catch (Exception e) {
            log.error("持久化模板失败", e);
        }
    }
}
