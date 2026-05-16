package com.dbexport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Template {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Long id;
    private String name;
    private String description;
    private String configJson;
    private Long createTime;
    private Long updateTime;

    public Template() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public ExportConfig toExportConfig() {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(configJson, ExportConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize configJson to ExportConfig", e);
        }
    }

    public void setExportConfig(ExportConfig exportConfig) {
        if (exportConfig == null) {
            this.configJson = null;
            return;
        }
        try {
            this.configJson = OBJECT_MAPPER.writeValueAsString(exportConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ExportConfig to configJson", e);
        }
    }
}