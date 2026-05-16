package com.dbexport.model;

import lombok.Data;

@Data
public class Template {
    private Long id;
    private String name;
    private String description;
    private String configJson;
    private Long createTime;
    private Long updateTime;
}
