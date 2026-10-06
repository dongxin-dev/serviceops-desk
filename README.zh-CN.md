# ServiceOps Desk

[English](README.md) | [简体中文](README.zh-CN.md)

ServiceOps Desk 是一个面向企业服务场景的工单与 SLA 协同平台，用于展示企业级 Java 系统的领域建模、业务流程、工程质量和全栈交付能力。

> 🚧 当前项目正在持续开发中。

## 项目目标

ServiceOps Desk 以真实企业服务管理场景为背景，使用现代 Java 与 Vue 技术栈实现完整的工单生命周期、SLA 管理、任务分配、升级机制、权限控制和审计能力。

## 计划功能

- 工单生命周期管理
- 工单分配与重新分配
- SLA 策略管理
- SLA 响应与解决时限跟踪
- 自动升级
- 基于角色的权限控制
- 操作审计
- 运营看板

## 计划技术栈

### 后端

- Java 21
- Spring Boot
- Spring Security
- MyBatis-Plus
- PostgreSQL
- Redis
- Flyway
- MapStruct
- OpenAPI / Swagger
- JUnit

### 前端

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- Axios
- ECharts

### 工程化

- Docker Compose
- Maven Wrapper
- GitHub Actions
- Conventional Commits（规范化提交）
- Flyway 数据库版本管理

## 项目目录

```text
serviceops-desk/
├── backend/        # 后端工程
├── frontend/       # 前端工程
├── docs/           # 架构与项目文档
├── docker/         # Docker 相关配置
├── scripts/        # 开发及辅助脚本
├── README.md
└── README.zh-CN.md