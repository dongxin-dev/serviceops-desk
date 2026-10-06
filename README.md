# ServiceOps Desk

[English](README.md) | [简体中文](README.zh-CN.md)

ServiceOps Desk is an enterprise service desk platform designed for ticket lifecycle management, SLA tracking, assignment, escalation, RBAC, and audit trails.

> 🚧 This project is under active development.

## Goals

ServiceOps Desk demonstrates the design and implementation of a production-oriented enterprise service management system using a modern Java and Vue technology stack.

## Planned Features

- Ticket lifecycle management
- Ticket assignment and reassignment
- SLA policy management
- SLA response and resolution tracking
- Automatic escalation
- Role-based access control
- Audit trails
- Operational dashboard

## Planned Tech Stack

### Backend

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

### Frontend

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- Axios
- ECharts

### Engineering

- Docker Compose
- Maven Wrapper
- GitHub Actions
- Conventional Commits
- Flyway database migrations

## Project Structure

```text
serviceops-desk/
├── backend/        # Backend application
├── frontend/       # Frontend application
├── docs/           # Architecture and project documentation
├── docker/         # Docker-related configuration
├── scripts/        # Development and utility scripts
├── README.md
└── README.zh-CN.md