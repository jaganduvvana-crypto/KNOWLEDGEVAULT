
# KNOWLEDGEVAULT
### Research Paper and Academic Knowledge Management System

## Project Overview

KNOWLEDGEVAULT is a web-based academic research management system developed using Java Spring Boot and MySQL.

The application provides a centralized platform to organize research papers, researchers, projects, keywords, citations and reviews.

It helps users explore academic publications, understand research relationships and manage research information through a structured relational database.

## Technologies Used

- Java 21
- Spring Boot
- Spring MVC
- Thymeleaf
- JDBC Template
- MySQL
- HTML5
- CSS3
- JavaScript
- Maven
- Visual Studio Code

## Main Features

### 1. Home
- Project introduction
- Research repository overview
- Navigation to all modules

### 2. Research Papers
- View research publications
- Search papers by title or keyword
- View complete paper information
- Manage paper records

### 3. Researchers
- View researcher profiles
- Search by name, institution or research area
- View researcher contributions
- Manage researcher records

### 4. Research Projects
- Explore research projects
- View project descriptions and dates
- View participating researchers
- Manage project records

### 5. Keywords
- Explore research keywords
- Find associated publications
- Search research topics
- Manage keyword records

### 6. Citations
- Explore paper citation relationships
- View citing and cited publications

### 7. Reviews
- View paper ratings
- Explore researcher comments and reviews

## Database Design

Database Name: `knowledgevault_db`

The database contains 12 relational tables:

1. researcher
2. research_area
3. research_project
4. publication_venue
5. keyword
6. paper
7. researcher_area
8. project_member
9. authorship
10. paper_keyword
11. citation
12. paper_review

Database concepts implemented:

- Primary and foreign keys
- Relational constraints
- Unique and NOT NULL constraints
- Database views
- Indexes
- Stored procedures
- SQL JOIN operations
- Transaction management

## Application Architecture

The application follows a three-layer architecture:

**Frontend**
- HTML
- CSS
- Thymeleaf
- JavaScript

**Backend**
- Java
- Spring Boot
- Spring MVC
- JdbcTemplate

**Database**
- MySQL

## Project Structure

```text
knowledgevault/
│
├── src/
│   ├── main/
│   │   ├── java/knowledgevault/
│   │   │   ├── KnowledgevaultApplication.java
│   │   │   ├── HomeController.java
│   │   │   ├── PaperController.java
│   │   │   ├── ResearcherController.java
│   │   │   ├── ProjectController.java
│   │   │   ├── KeywordController.java
│   │   │   ├── CitationController.java
│   │   │   ├── ReviewController.java
│   │   │   └── Management Controllers
│   │   │
│   │   └── resources/
│   │       ├── templates/
│   │       ├── static/
│   │       │   ├── css/
│   │       │   └── js/
│   │       └── application.properties
│   │
│   └── test/
│
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

## Installation and Setup

### Prerequisites

- JDK 21
- MySQL Server
- MySQL Workbench
- Visual Studio Code

### Step 1: Clone the Repository

```bash
git clone YOUR_GITHUB_REPOSITORY_URL
```

### Step 2: Open the Project

```bash
cd knowledgevault
```

### Step 3: Configure MySQL

Create or import the database:

```sql
CREATE DATABASE knowledgevault_db;
```

Ensure that all 12 required tables and their data are available.

### Step 4: Configure application.properties

Create the local file:

`src/main/resources/application.properties`

Add the following configuration and enter your own MySQL password:

```properties
spring.application.name=knowledgevault

server.port=8080

spring.datasource.url=jdbc:mysql://localhost:3306/knowledgevault_db?useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true

spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD

spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.datasource.hikari.maximum-pool-size=5
```

### Step 5: Build the Application

On Windows PowerShell:

```powershell
.\mvnw.cmd clean package
```

### Step 6: Run the Application

```powershell
.\mvnw.cmd spring-boot:run
```

### Step 7: Open in Browser

```text
http://localhost:8080
```

## Design

- Dark and light theme
- Charcoal interface
- Cyan and teal accent colours
- IBM Plex Mono typography
- Responsive layouts
- Shared navigation and footer

## Author

**Student Name:** Guna Sekhar V  
**Register Number:** 24BCE2107

## Project Status

Development and functional testing completed for the main application pages and management modules.
