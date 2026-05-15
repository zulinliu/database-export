#!/usr/bin/env python3
"""
数据库导出工具 V2.0 - Playwright端到端测试
测试覆盖：登录、主页面布局、主题切换、数据库连接、导出模式、模板管理、导出记录
"""

from playwright.sync_api import sync_playwright, Page, expect
import time
import sys

BASE_URL = "http://localhost:8080"
TEST_USERNAME = "admin"
TEST_PASSWORD = "123456"


def test_login_page_loads(page: Page):
    """测试1: 登录页面正常加载"""
    print("\n=== 测试1: 登录页面加载 ===")
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')

    # 验证页面标题
    title = page.title()
    assert "数据库导出工具" in title, f"页面标题不符: {title}"
    print(f"  ✓ 页面标题正确: {title}")

    # 验证登录表单元素
    assert page.locator("#username").is_visible(), "用户名输入框不可见"
    assert page.locator("#password").is_visible(), "密码输入框不可见"
    assert page.locator("#loginBtn").is_visible(), "登录按钮不可见"
    print("  ✓ 登录表单元素完整")

    # 验证默认账号提示
    hint = page.locator(".login-hint").text_content()
    assert "admin" in hint, "默认账号提示不正确"
    print("  ✓ 默认账号提示正确")

    page.screenshot(path="/tmp/01_login_page.png", full_page=True)
    print("  ✓ 截图已保存: /tmp/01_login_page.png")


def test_login_success(page: Page):
    """测试2: 登录成功"""
    print("\n=== 测试2: 登录成功 ===")
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')

    page.fill("#username", TEST_USERNAME)
    page.fill("#password", TEST_PASSWORD)
    page.click("#loginBtn")

    # 等待跳转
    page.wait_for_url("**/index**", timeout=10000)
    print(f"  ✓ 登录成功，跳转到: {page.url}")

    # 验证主页面元素
    assert page.locator(".top-bar").is_visible(), "顶部栏不可见"
    assert page.locator(".nav-tab[data-tab='export']").is_visible(), "导出Tab不可见"
    print("  ✓ 主页面布局正确")

    page.screenshot(path="/tmp/02_main_page.png", full_page=True)
    print("  ✓ 截图已保存: /tmp/02_main_page.png")


def test_login_failure(page: Page):
    """测试3: 登录失败"""
    print("\n=== 测试3: 登录失败 ===")
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')

    page.fill("#username", "wronguser")
    page.fill("#password", "wrongpass")
    page.click("#loginBtn")

    # 等待错误提示
    page.wait_for_selector(".login-error", timeout=5000)
    error_text = page.locator(".login-error").text_content()
    assert len(error_text) > 0, "错误提示为空"
    print(f"  ✓ 登录失败提示: {error_text}")
    page.screenshot(path="/tmp/03_login_error.png", full_page=True)


def test_theme_toggle(page: Page):
    """测试4: 主题切换"""
    print("\n=== 测试4: 主题切换 ===")

    # 先登录
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')
    page.fill("#username", TEST_USERNAME)
    page.fill("#password", TEST_PASSWORD)
    page.click("#loginBtn")
    page.wait_for_url("**/index**", timeout=10000)

    # 检查初始主题
    initial_theme = page.evaluate("document.documentElement.getAttribute('data-theme')")
    print(f"  初始主题: {initial_theme}")

    # 切换主题
    page.click("#themeToggle")
    page.wait_for_timeout(500)
    new_theme = page.evaluate("document.documentElement.getAttribute('data-theme')")
    print(f"  切换后主题: {new_theme}")
    assert initial_theme != new_theme, "主题未切换"
    print("  ✓ 主题切换成功")

    # 验证主题持久化
    theme_in_storage = page.evaluate("localStorage.getItem('theme')")
    assert theme_in_storage == new_theme, "主题未保存到localStorage"
    print("  ✓ 主题已持久化到localStorage")

    page.screenshot(path="/tmp/04_theme_toggle.png", full_page=True)


def test_connection_form(page: Page):
    """测试5: 数据库连接表单"""
    print("\n=== 测试5: 数据库连接表单 ===")

    # 登录
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')
    page.fill("#username", TEST_USERNAME)
    page.fill("#password", TEST_PASSWORD)
    page.click("#loginBtn")
    page.wait_for_url("**/index**", timeout=10000)

    # 验证表单元素
    assert page.locator("#dbType").is_visible(), "数据库类型选择器不可见"
    assert page.locator("#dbHost").is_visible(), "主机地址输入框不可见"
    assert page.locator("#dbPort").is_visible(), "端口输入框不可见"
    assert page.locator("#dbName").is_visible(), "数据库名输入框不可见"
    assert page.locator("#dbUser").is_visible(), "用户名输入框不可见"
    assert page.locator("#dbPass").is_visible(), "密码输入框不可见"
    print("  ✓ 连接表单完整")

    # 验证默认端口切换
    page.select_option("#dbType", "mysql")
    port = page.input_value("#dbPort")
    assert port == "3306", f"MySQL默认端口错误: {port}"
    print(f"  ✓ MySQL默认端口正确: {port}")

    page.select_option("#dbType", "dm")
    port = page.input_value("#dbPort")
    assert port == "5236", f"DM默认端口错误: {port}"
    print(f"  ✓ DM默认端口正确: {port}")

    # 验证测试连接按钮
    assert page.locator("#testConnBtn").is_visible(), "测试连接按钮不可见"
    assert page.locator("#connectBtn").is_visible(), "连接数据库按钮不可见"
    print("  ✓ 按钮元素完整")


def test_export_mode_tabs(page: Page):
    """测试6: 导出模式Tab切换"""
    print("\n=== 测试6: 导出模式Tab切换 ===")

    # 登录
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')
    page.fill("#username", TEST_USERNAME)
    page.fill("#password", TEST_PASSWORD)
    page.click("#loginBtn")
    page.wait_for_url("**/index**", timeout=10000)

    # 验证三个导出模式Tab
    assert page.locator(".tabs .tab[data-export-type='customTables']").is_visible(), "自定义表名Tab不可见"
    assert page.locator(".tabs .tab[data-export-type='tableSelect']").is_visible(), "表勾选Tab不可见"
    assert page.locator(".tabs .tab[data-export-type='customSql']").is_visible(), "自定义SQL Tab不可见"
    print("  ✓ 三种导出模式Tab存在")

    # 切换到表勾选模式
    page.click(".tabs .tab[data-export-type='tableSelect']")
    assert page.locator("#exportType-tableSelect").is_visible(), "表勾选内容区不可见"
    print("  ✓ 表勾选模式切换正确")

    # 切换到自定义SQL模式
    page.click(".tabs .tab[data-export-type='customSql']")
    assert page.locator("#exportType-customSql").is_visible(), "自定义SQL内容区不可见"
    assert page.locator("#customSqlInput").is_visible(), "SQL输入框不可见"
    assert page.locator("#validateSqlBtn").is_visible(), "验证SQL按钮不可见"
    print("  ✓ 自定义SQL模式切换正确")

    # 切换回自定义表名模式
    page.click(".tabs .tab[data-export-type='customTables']")
    assert page.locator("#exportType-customTables").is_visible(), "自定义表名内容区不可见"
    assert page.locator("#customTablesInput").is_visible(), "表名输入框不可见"
    print("  ✓ 自定义表名模式切换正确")


def test_filter_panels(page: Page):
    """测试7: 过滤面板"""
    print("\n=== 测试7: 过滤面板 ===")

    # 登录
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')
    page.fill("#username", TEST_USERNAME)
    page.fill("#password", TEST_PASSWORD)
    page.click("#loginBtn")
    page.wait_for_url("**/index**", timeout=10000)

    # 验证时间过滤开关
    time_filter_toggle = page.locator("#enableTimeFilter")
    assert time_filter_toggle.is_visible(), "时间过滤开关不可见"
    page.click("label:has(#enableTimeFilter)")
    page.wait_for_timeout(300)
    assert page.locator("#timeFilterPanel").is_visible(), "时间过滤面板未展开"
    print("  ✓ 时间过滤面板展开正确")

    # 验证字段过滤开关
    field_filter_toggle = page.locator("#enableFieldFilter")
    assert field_filter_toggle.is_visible(), "字段过滤开关不可见"
    page.click("label:has(#enableFieldFilter)")
    page.wait_for_timeout(300)
    assert page.locator("#fieldFilterPanel").is_visible(), "字段过滤面板未展开"
    print("  ✓ 字段过滤面板展开正确")

    # 验证字段过滤类型切换
    page.select_option("#fieldFilterType", "IN")
    assert page.locator("#filterValueLabel").text_content() == "参数值（逗号分隔）", "IN类型标签不正确"
    print("  ✓ 字段过滤类型切换正确")


def test_nav_tabs(page: Page):
    """测试8: 主导航Tab"""
    print("\n=== 测试8: 主导航Tab ===")

    # 登录
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')
    page.fill("#username", TEST_USERNAME)
    page.fill("#password", TEST_PASSWORD)
    page.click("#loginBtn")
    page.wait_for_url("**/index**", timeout=10000)

    # 验证三个主导航Tab
    assert page.locator(".nav-tab[data-tab='export']").is_visible(), "导出Tab不可见"
    assert page.locator(".nav-tab[data-tab='templates']").is_visible(), "模板Tab不可见"
    assert page.locator(".nav-tab[data-tab='records']").is_visible(), "记录Tab不可见"
    print("  ✓ 三个主导航Tab存在")

    # 切换到模板管理
    page.click(".nav-tab[data-tab='templates']")
    assert page.locator("#tab-templates").is_visible(), "模板Tab内容不可见"
    assert page.locator("#templateSearchInput").is_visible(), "模板搜索框不可见"
    print("  ✓ 模板管理Tab切换正确")

    # 切换到导出记录
    page.click(".nav-tab[data-tab='records']")
    assert page.locator("#tab-records").is_visible(), "记录Tab内容不可见"
    assert page.locator("#cleanOldRecordsBtn").is_visible(), "清理按钮不可见"
    print("  ✓ 导出记录Tab切换正确")

    # 切回导出
    page.click(".nav-tab[data-tab='export']")
    assert page.locator("#tab-export").is_visible(), "导出Tab内容不可见"
    print("  ✓ 导出Tab切换正确")


def test_export_config(page: Page):
    """测试9: 导出配置"""
    print("\n=== 测试9: 导出配置 ===")

    # 登录
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')
    page.fill("#username", TEST_USERNAME)
    page.fill("#password", TEST_PASSWORD)
    page.click("#loginBtn")
    page.wait_for_url("**/index**", timeout=10000)

    # 验证导出格式复选框
    assert page.locator("#exportExcel").is_visible(), "Excel复选框不可见"
    assert page.locator("#exportSql").is_visible(), "SQL复选框不可见"
    assert page.locator("#exportExcel").is_checked(), "Excel默认应选中"
    assert page.locator("#exportSql").is_checked(), "SQL默认应选中"
    print("  ✓ 导出格式默认选中正确")

    # 验证并发数选择
    assert page.locator("#maxConnections").is_visible(), "并发数选择器不可见"
    options = page.locator("#maxConnections option").count()
    assert options == 10, f"并发数选项数量错误: {options}"
    print("  ✓ 并发数选择器正确")

    # 验证保存模板按钮
    assert page.locator("#saveTemplateBtn").is_visible(), "保存模板按钮不可见"
    assert page.locator("#startExportBtn").is_visible(), "开始导出按钮不可见"
    print("  ✓ 操作按钮完整")


def test_logout(page: Page):
    """测试10: 登出功能"""
    print("\n=== 测试10: 登出功能 ===")

    # 登录
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state('networkidle')
    page.fill("#username", TEST_USERNAME)
    page.fill("#password", TEST_PASSWORD)
    page.click("#loginBtn")
    page.wait_for_url("**/index**", timeout=10000)

    # 点击登出
    page.click("#logoutBtn")
    page.wait_for_url("**/login**", timeout=10000)
    print(f"  ✓ 登出成功，跳转到: {page.url}")

    # 验证需要重新登录
    assert page.locator("#username").is_visible(), "未返回登录页"
    print("  ✓ 需要重新登录")


def test_api_endpoints(page: Page):
    """测试11: API端点认证拦截"""
    print("\n=== 测试11: API端点认证拦截 ===")

    # 测试未登录访问API
    response = page.request.get(f"{BASE_URL}/api/checkLogin")
    assert response.status == 401, f"未登录应返回401，实际: {response.status}"
    print(f"  ✓ /api/checkLogin 未登录返回: {response.status}")

    response = page.request.get(f"{BASE_URL}/api/database/tables")
    assert response.status == 401, f"未登录应返回401，实际: {response.status}"
    print(f"  ✓ /api/database/tables 未登录返回: {response.status}")

    response = page.request.post(f"{BASE_URL}/api/export/start", data="{}")
    assert response.status == 401, f"未登录应返回401，实际: {response.status}"
    print(f"  ✓ /api/export/start 未登录返回: {response.status}")


def run_all_tests():
    """运行所有测试"""
    print("=" * 60)
    print("数据库导出工具 V2.0 - Playwright端到端测试")
    print("=" * 60)

    with sync_playwright() as p:
        # 启动浏览器
        browser = p.chromium.launch(
            headless=True,
            args=["--no-sandbox", "--disable-dev-shm-usage"]
        )

        results = []
        test_functions = [
            ("登录页面加载", test_login_page_loads),
            ("登录成功", test_login_success),
            ("登录失败", test_login_failure),
            ("主题切换", test_theme_toggle),
            ("数据库连接表单", test_connection_form),
            ("导出模式Tab", test_export_mode_tabs),
            ("过滤面板", test_filter_panels),
            ("主导航Tab", test_nav_tabs),
            ("导出配置", test_export_config),
            ("登出功能", test_logout),
            ("API端点认证", test_api_endpoints),
        ]

        for test_name, test_func in test_functions:
            try:
                page = browser.new_page(viewport={"width": 1280, "height": 720})
                test_func(page)
                results.append((test_name, "PASS"))
                page.close()
            except Exception as e:
                try:
                    page.screenshot(path=f"/tmp/error_{test_name.replace(' ', '_')}.png")
                except:
                    pass
                results.append((test_name, f"FAIL: {str(e)}"))
                try:
                    page.close()
                except:
                    pass

        browser.close()

    # 打印测试结果
    print("\n" + "=" * 60)
    print("测试结果汇总")
    print("=" * 60)

    passed = 0
    failed = 0
    for test_name, result in results:
        if result == "PASS":
            print(f"  ✅ {test_name}: PASS")
            passed += 1
        else:
            print(f"  ❌ {test_name}: {result}")
            failed += 1

    print("-" * 60)
    print(f"总计: {passed} 通过, {failed} 失败, {len(results)} 总计")
    print("=" * 60)

    # 截图已保存提示
    print("\n截图已保存到 /tmp/ 目录:")
    print("  - 01_login_page.png")
    print("  - 02_main_page.png")
    print("  - 03_login_error.png")
    print("  - 04_theme_toggle.png")

    return failed == 0


if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)
