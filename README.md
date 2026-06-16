# SADNA Ticketing System

## Project Overview

This project is a full-stack event ticketing application developed to support online event discovery, ticket purchasing, lottery registration, user account management, and administrative oversight. The system combines a modern frontend interface with a backend service that handles business logic, authentication, data persistence, and external integrations.

The repository contains two main components:
- a React + TypeScript frontend for the user-facing application
- a Java Spring Boot backend that provides the API and domain logic
- a SQL schema used to define the database structure

## Objectives

The main goals of this project are to:
- provide a user-friendly platform for browsing and purchasing event tickets
- support company-side event and policy management
- enable lottery-based and queue-based ticket access flows
- implement role-based access for regular users, company managers, and administrators
- demonstrate the integration of frontend, backend, and database components in a complete software system

## Key Features

### User Features
- user registration and login
- event search and browsing
- event details and ticket purchase flow
- lottery registration and status tracking
- purchase history and active purchase monitoring
- notifications for important updates

### Company Features
- company creation and management
- event creation and editing
- configuration of purchase policies, discounts, and layout settings
- access to sales and event-related information for authorized users

### Admin Features
- dashboard and analytics views
- complaint and subscriber management
- queue supervision and system-level monitoring
- administrative notification tools

## Technologies Used

### Frontend
- React
- TypeScript
- Vite
- Axios
- CSS for interface styling

### Backend
- Java 17
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- Maven
- PostgreSQL

## Repository Structure

- `frontend/` — frontend application source code
- `Version1/` — backend application source code and configuration
- `schema.sql` — SQL database schema
- `package.json` — root project metadata

## Prerequisites

To run this project locally, the following tools are required:
- Node.js and npm
- Java 17 or higher
- Maven
- PostgreSQL (or an equivalent configured database)

## Backend Setup

1. Navigate to the backend folder:
   ```bash
   cd Version1
   ```
2. Build the project:
   ```bash
   mvn clean install
   ```
3. Start the backend server:
   ```bash
   mvn spring-boot:run
   ```

The backend configuration is defined under the resources folder. If a different database setup is needed, update the relevant application settings before running the server.

## Frontend Setup

1. Navigate to the frontend folder:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm run dev
   ```

The frontend is configured to communicate with the backend API during development.

## Running the Application

1. Start the backend server.
2. Start the frontend server.
3. Open the local frontend URL shown by Vite (typically `http://localhost:5173`).

## Quick User Guide

Once the application is running, the main user flows are:

1. **Register or log in** using the authentication pages.
2. **Browse events** from the main event search page.
3. **Open an event** to view details, ticket availability, and purchase options.
4. **Purchase tickets** or join a lottery/queue flow when applicable.
5. **Manage company-related actions** from the company dashboard if you are a company owner or manager.
6. **Use the admin dashboard** to review analytics, complaints, subscribers, and queue information if you have admin access.

## Database Design

The database schema is defined in `schema.sql`. It includes tables for users, companies, events, purchases, tickets, notifications, complaints, and admin-related records.

## Testing

Backend tests can be executed with:
```bash
cd Version1
mvn test
```

Frontend build verification can be performed with:
```bash
cd frontend
npm run build
```

## Notes

- The frontend stores session-related information in local storage.
- The backend exposes multiple REST endpoints for users, events, purchases, notifications, and administrative actions.
- This project is suitable for academic demonstration and development purposes, and production security settings should be reviewed before deployment.

