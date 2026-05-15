package com.dbexport.service;

import com.dbexport.model.ExportConfig;
import com.dbexport.model.Template;
import com.dbexport.util.FileUtil;
import com.dbexport.util.JsonUtil;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class TemplateService {

    private static final String TEMPLATE_DIR = "./templates";
    private static final String INDEX_FILE = "templates_index.json";
    private final AtomicLong idGenerator = new AtomicLong(1);

    @PostConstruct
    public void init() {
        FileUtil.ensureDirectoryExists(TEMPLATE_DIR);
        List<Template> existing = loadAllFromDisk();
        long maxId = existing.stream()
                .mapToLong(Template::getId)
                .max()
                .orElse(0L);
        idGenerator.set(maxId + 1);
    }

    public Template save(Template template) {
        long now = System.currentTimeMillis();

        if (template.getId() == null) {
            template.setId(idGenerator.getAndIncrement());
            template.setCreateTime(now);
        } else {
            Template existing = loadFromDisk(template.getId());
            if (existing != null) {
                template.setCreateTime(existing.getCreateTime());
            } else {
                template.setCreateTime(now);
            }
        }

        template.setUpdateTime(now);
        writeToDisk(template);
        updateIndex();
        return template;
    }

    public List<Template> listAll() {
        return loadAllFromDisk();
    }

    public Template getById(Long id) {
        if (id == null) {
            return null;
        }
        return loadFromDisk(id);
    }

    public boolean deleteById(Long id) {
        if (id == null) {
            return false;
        }
        File file = getTemplateFile(id);
        boolean deleted = file.exists() && file.delete();
        if (deleted) {
            updateIndex();
        }
        return deleted;
    }

    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long id : ids) {
            if (deleteById(id)) {
                count++;
            }
        }
        if (count > 0) {
            updateIndex();
        }
        return count;
    }

    public String exportTemplates(List<Long> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Template IDs must not be null or empty");
        }

        String tempDir = TEMPLATE_DIR + "/export_" + UUID.randomUUID().toString().substring(0, 8);
        FileUtil.ensureDirectoryExists(tempDir);

        List<File> filesToZip = new ArrayList<>();
        for (Long id : ids) {
            Template template = loadFromDisk(id);
            if (template == null) {
                continue;
            }
            File sourceFile = getTemplateFile(id);
            File destFile = new File(tempDir, id + ".json");
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            filesToZip.add(destFile);
        }

        String zipFileName = TEMPLATE_DIR + "/export_" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
        File zipFile = FileUtil.createZipFromFiles(filesToZip, zipFileName);
        FileUtil.deleteDirectory(new File(tempDir));

        return zipFile.getAbsolutePath();
    }

    public List<Template> importTemplates(String zipFilePath) throws IOException {
        File zipFile = new File(zipFilePath);
        if (!zipFile.exists()) {
            throw new IllegalArgumentException("ZIP file not found: " + zipFilePath);
        }

        String tempDir = TEMPLATE_DIR + "/import_" + UUID.randomUUID().toString().substring(0, 8);
        FileUtil.ensureDirectoryExists(tempDir);
        FileUtil.extractZip(zipFile, tempDir);

        List<Template> imported = new ArrayList<>();
        File dir = new File(tempDir);
        File[] jsonFiles = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (jsonFiles != null) {
            for (File jsonFile : jsonFiles) {
                try {
                    String content = new String(Files.readAllBytes(jsonFile.toPath()));
                    Template template = JsonUtil.fromJson(content, Template.class);
                    if (template != null) {
                        template.setId(null);
                        template.setCreateTime(null);
                        template.setUpdateTime(null);
                        Template saved = save(template);
                        imported.add(saved);
                    }
                } catch (Exception e) {
                    // skip invalid files
                }
            }
        }

        FileUtil.deleteDirectory(new File(tempDir));
        return imported;
    }

    public int count() {
        return loadAllFromDisk().size();
    }

    public List<Template> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return listAll();
        }
        String lowerKeyword = keyword.toLowerCase();
        return loadAllFromDisk().stream()
                .filter(t -> t.getName() != null && t.getName().toLowerCase().contains(lowerKeyword))
                .collect(Collectors.toList());
    }

    private void updateIndex() {
        List<Template> templates = loadAllFromDisk();
        List<Map<String, Object>> indexEntries = new ArrayList<>();
        for (Template t : templates) {
            indexEntries.add(Map.of("id", t.getId(), "name", t.getName() != null ? t.getName() : ""));
        }
        String indexPath = TEMPLATE_DIR + "/" + INDEX_FILE;
        try {
            String json = JsonUtil.toJson(indexEntries);
            Files.write(Paths.get(indexPath), json.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to update index file", e);
        }
    }

    private File getTemplateFile(Long id) {
        return new File(TEMPLATE_DIR, id + ".json");
    }

    private void writeToDisk(Template template) {
        String json = JsonUtil.toJson(template);
        File file = getTemplateFile(template.getId());
        try {
            Files.write(file.toPath(), json.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write template to disk: " + template.getId(), e);
        }
    }

    private Template loadFromDisk(Long id) {
        File file = getTemplateFile(id);
        if (!file.exists()) {
            return null;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            return JsonUtil.fromJson(content, Template.class);
        } catch (IOException e) {
            return null;
        }
    }

    private List<Template> loadAllFromDisk() {
        List<Template> templates = new ArrayList<>();
        File dir = new File(TEMPLATE_DIR);
        File[] files = dir.listFiles((d, name) -> name.matches("\\d+\\.json"));
        if (files != null) {
            for (File file : files) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()));
                    Template template = JsonUtil.fromJson(content, Template.class);
                    if (template != null) {
                        templates.add(template);
                    }
                } catch (IOException e) {
                    // skip unreadable files
                }
            }
        }
        templates.sort(Comparator.comparing(Template::getId));
        return templates;
    }
}