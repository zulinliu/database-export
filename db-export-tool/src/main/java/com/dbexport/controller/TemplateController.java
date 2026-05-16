package com.dbexport.controller;

import com.dbexport.model.ApiResponse;
import com.dbexport.model.Template;
import com.dbexport.service.TemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/template")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/list")
    public ApiResponse<?> listTemplates(@RequestParam(required = false) String keyword) {
        try {
            List<Template> templates;
            if (keyword != null && !keyword.trim().isEmpty()) {
                templates = templateService.search(keyword);
            } else {
                templates = templateService.listAll();
            }
            return ApiResponse.success(templates);
        } catch (Exception e) {
            return ApiResponse.error("获取模板列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/save")
    public ApiResponse<?> saveTemplate(@RequestBody Template template) {
        try {
            if (template.getName() == null || template.getName().trim().isEmpty()) {
                return ApiResponse.error(400, "模板名称不能为空");
            }

            Template saved = templateService.save(template);
            return ApiResponse.success("保存成功", saved);
        } catch (Exception e) {
            return ApiResponse.error("保存模板失败: " + e.getMessage());
        }
    }

    @GetMapping("/load")
    public ApiResponse<?> loadTemplate(@RequestParam String id) {
        try {
            Template template = templateService.load(id);
            if (template == null) {
                return ApiResponse.error(404, "模板不存在");
            }
            return ApiResponse.success(template);
        } catch (Exception e) {
            return ApiResponse.error("加载模板失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<?> deleteTemplate(@PathVariable String id) {
        try {
            boolean deleted = templateService.delete(id);
            if (deleted) {
                return ApiResponse.success("删除成功");
            } else {
                return ApiResponse.error(404, "模板不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("删除模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/deleteBatch")
    public ApiResponse<?> deleteBatch(@RequestBody List<String> ids) {
        try {
            int count = templateService.deleteBatch(ids);
            return ApiResponse.success("成功删除 " + count + " 个模板");
        } catch (Exception e) {
            return ApiResponse.error("批量删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/export")
    public ApiResponse<?> exportTemplates(@RequestParam List<String> ids) {
        try {
            String zipPath = templateService.exportTemplates(ids);
            return ApiResponse.success(zipPath);
        } catch (Exception e) {
            return ApiResponse.error("导出模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/import")
    public ApiResponse<?> importTemplates(@RequestBody String zipBase64) {
        try {
            int count = templateService.importTemplates(zipBase64);
            return ApiResponse.success("成功导入 " + count + " 个模板");
        } catch (Exception e) {
            return ApiResponse.error("导入模板失败: " + e.getMessage());
        }
    }
}
