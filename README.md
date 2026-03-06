# Kafka Lab

<div align="center">

![Java](https://img.shields.io/badge/Java-17%2B-3f7cff?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![Vite](https://img.shields.io/badge/Vite-5-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-Lab-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)

**A modern visual playground for learning Kafka, validating messaging flows, and simulating real-world behavior.**

</div>

---

## ✨ What is Kafka Lab?

Kafka Lab is a full-stack, visual Kafka experimentation platform. It helps you:

- create and manage topics quickly
- operate managed producers and consumers
- watch real-time activity streams
- run scenario-driven experiments
- inspect cluster topology and message paths visually
- manage Kafka lab environments from a control plane

It is designed for **learning**, **demo**, **POC validation**, and **team training**.

---

## 🧭 Architecture

```text
Kafka Lab UI (React + Ant Design + Vite)
        |
        | REST + WebSocket
        v
Kafka Lab Server (Spring Boot)
  |- Topic / Producer / Consumer APIs
  |- Scenario Engine
  |- Environment Manager (docker compose orchestration)
  |- Realtime Event Broadcasting
        |
        v
Kafka Cluster (multi-broker lab env) + Kafka UI
```

---

## 🚀 Core Capabilities

### 1) Messaging Console
- Topic creation / deletion
- Managed Producer lifecycle (register / update topics / delete)
- Managed Consumer lifecycle (register / start / stop / update topics / delete)
- Message send with key / partition / count / delay / transactional toggle

### 2) Activity Logs (Realtime)
- Centralized operation/activity stream
- Live push to UI via WebSocket
- Search, filter, and chronological browsing

### 3) Topology Studio
- Visualized producers → brokers/partitions → consumers
- Realtime animation of send/receive flow
- Partition activity and consumer receive feedback

### 4) Scenario Engine
- Rebalance simulation
- Consumer lag simulation
- Exactly-once flow exploration
- Broker crash recovery walkthrough
- Poison message / DLQ flow guidance

### 5) Environment Control Plane
- Create/start/stop/delete isolated Kafka lab environments
- Generate per-environment Docker Compose runtime
- Query environment status and logs

---

## 🗂 Project Structure

```text
.
├─ docs/                    # architecture and scenario docs
├─ kafka-lab-server/        # Spring Boot backend
├─ kafka-lab-ui/            # React frontend
└─ runtime/                 # generated runtime env artifacts (gitignored)
```

---

## ⚡ Quick Start

### Prerequisites
- Docker + Docker Compose
- JDK 17+
- Node.js 20+
- Maven 3.9+

### Local dev split mode

Backend:
```bash
cd kafka-lab-server
mvn spring-boot:run
```

Frontend:
```bash
cd kafka-lab-ui
npm install
npm run dev
```

Open:
- Kafka Lab UI: http://localhost:5173
- Kafka Lab Server: http://localhost:8080

Then create/start lab Kafka environments from the UI (**Environments** page).

---

## 🔌 API Surface (high-level)

- Topics: `/topics`
- Producer: `/producer/*`
- Consumer: `/consumer/*`
- Dashboard/Cluster: `/dashboard` `/cluster` `/brokers`
- Scenario: `/scenario/run`
- Environment manager: `/env/*`
- WebSocket events: `/ws/events`

---

## ⚙️ Key Runtime Configuration

Backend supports environment-driven config:

- `KAFKA_BOOTSTRAP_SERVERS`
- `KAFKA_LAB_ENV_ROOT`
- `KAFKA_LAB_WORKSPACE_ROOT`
- `KAFKA_LAB_DOCKER_CONFIG` (optional)

See [kafka-lab-server/src/main/resources/application.yml](kafka-lab-server/src/main/resources/application.yml).

---

## 🔐 Security Notes

This repository is tuned for lab usage by default.
For public or team-shared deployment, you should add:

- authentication/authorization for control-plane APIs
- role-based access control for environment operations
- stricter CORS/origin policy for WebSocket and REST

---

## 🛣 Roadmap

- richer scenario orchestration and assertions
- stronger metrics/observability integration
- packaged deployment profiles for team environments
- multi-tenant lab governance support

---

## 📚 Documentation

- [docs/architecture.md](docs/architecture.md)
- [docs/scenarios.md](docs/scenarios.md)

---

## 🤝 Contributing

PRs and issue reports are welcome.
For major changes, please open an issue first to discuss scope and design.

---

## 📄 License

This project is licensed under the MIT License.

See [LICENSE](LICENSE) for full text.
