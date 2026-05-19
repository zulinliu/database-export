# Findings & Decisions

## Requirements
- 参考 `design_example/` 中的设计图优化前端美观度。
- 只参考风格、样式、视觉细节，不参考功能设计。
- 重点优化左侧导航菜单栏、提醒框、表格样式、按钮间距等细节。
- 先全面检索和学习，深度思考后输出设计方案与规划。
- 本轮不实施代码改动。

## Research Findings
- 当前前端文件集中在 `src/main/resources/templates/index.html`、`src/main/resources/templates/login.html`、`src/main/resources/static/css/style.css`、`src/main/resources/static/js/main.js`。
- 当前页面已经具备卡片、弹窗、Toast、表格、左侧侧栏等基本结构，说明视觉升级主要是样式系统与细节统一问题，不需要先做结构重构。
- 当前样式基调偏通用后台模板风格，颜色、阴影、边框、交互态层级都较保守，缺少鲜明的品牌感和面板节奏感。
- 参考图整体采用“高亮蓝 + 雾白背景 + 大圆角 + 轻阴影”的现代企业后台视觉语言，画面更轻、更稳，也更强调页面留白与模块呼吸感。
- 参考图中的主按钮、选中导航、焦点模块都使用高纯度蓝色作为主视觉锚点，其余元素则保持低对比中性背景，形成很清晰的主次关系。
- 当前前端存在“结构已更新，但样式与脚本未完全跟上”的断层：`index.html` 已经切到侧边栏布局，但 `style.css` 仍保留旧版顶部导航样式，且缺少 `.sidebar`、`.main-content`、`.mode-tab` 等关键类定义。
- `main.js` 的页面切换与模式切换仍在操作 `.nav-tabs .nav-tab`，与 `index.html` 中的 `.sidebar-item`、`.mode-tab` 不一致；这说明后续视觉实施前需要先统一命名与状态钩子，但不涉及功能逻辑改造。
- `index.html`、`main.js` 中存在大量 inline style 和 JS 内联 HTML 片段，导致按钮间距、提示框内容块、状态文案容器的样式难以统一，这会是后续提升质感时的主要维护成本来源。
- 当前模板与脚本中共检出 `106` 处内联样式，说明现阶段视觉差异不是单个组件问题，而是缺少统一样式抽象的问题。
- `数据库导出工具V2_0设计方案.md` 中的 UI 布局仍描述旧版顶部栏结构，与当前 `index.html` 的侧栏布局不一致，因此正式实施时应以当前页面骨架为准，参考设计图补齐视觉而不是回退结构。
- 实施时保留了现有功能入口和业务字段，只重构了视觉层、状态类和部分辅助文案，不触碰接口调用与核心数据流程。
- 新样式以全局设计令牌为中心，替换了原先“局部 patch”式写法，重点覆盖侧栏、卡片、表格、弹窗、Toast、登录页和按钮系统。

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| 先做“视觉语言提炼”再做“组件级规划” | 避免直接列样式修改项而缺少统一审美标准 |
| 以 CSS 设计令牌、容器层级、状态反馈、密度控制为核心分析框架 | 这几项直接决定当前系统的质感和一致性 |
| 用小范围 HTML 语义补强替代大规模 DOM 重写 | 保持功能稳定，同时给样式系统足够的落点 |
| 用统一的结果卡片和状态 pill 取代 JS 内联色块反馈 | 让提醒框、导出结果、确认框的视觉语言一致 |

## Issues Encountered
| Issue | Resolution |
|-------|------------|
| 之前会话中的沙箱导致基础读取命令失败 | 当前会话权限已切换，继续按正常流程分析 |
| 当前前端存在样式类名与脚本钩子错位 | 在方案中将其定义为“视觉实施前的结构对齐步骤”，不作为功能变更处理 |

## Resources
- `design_example/01-login-admin.png`
- `design_example/03-export-table-picker.png`
- `design_example/12-save-template-modal.png`
- `src/main/resources/templates/index.html`
- `src/main/resources/templates/login.html`
- `src/main/resources/static/css/style.css`
- `src/main/resources/static/js/main.js`

## Visual/Browser Findings
- `01-login-admin.png`：登录页不是纯居中卡片，而是“左侧品牌说明 + 右侧登录卡片”的双栏布局；背景带淡蓝雾面渐变，登录卡片圆角较大、阴影柔和，整体看起来更像企业级产品首页而不是默认后台登录框。
- `01-login-admin.png`：标题字号大、字重高，副文案与能力标签卡片拉开节奏；标签卡片使用高留白、小圆角、轻边框/阴影，强调稳定和专业感。
- `03-export-table-picker.png`：左侧导航为深色整栏，logo 区、分组标题、导航按钮层次非常明确；当前激活项使用亮蓝胶囊形高亮，并带内嵌图标底片和较强的悬浮感。
- `03-export-table-picker.png`：主内容区为浅灰蓝背景上的白色大卡片系统，卡片圆角偏大，模块间距充足；表单、分段 tab、按钮、列表项都沿用统一圆角与细描边，细节一致性明显优于当前实现。
- `12-save-template-modal.png`：弹窗采用更深的背景遮罩和柔和大阴影，主体卡片边缘圆润、内边距宽松；标题、副文案、表单、按钮之间的垂直节奏非常清晰，没有当前实现那种“能用但偏平”的感觉。
- `12-save-template-modal.png`：弹窗底部按钮强调主次分层，次按钮为白底描边，主按钮为高亮蓝实底；输入框边界非常轻，更多依靠留白和容器轮廓建立结构，整体显得更高级。

---
*Update this file after every 2 view/browser/search operations*
*This prevents visual information from being lost*
