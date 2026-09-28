# E-commerce Support Agent

An end-to-end support-email demo built with Spring Boot, Spring AI, and the Model Context Protocol (MCP). The client watches a local Mailpit inbox, uses an OpenAI chat model and tools exposed by the MCP server to investigate each email, then sends a reply and records the resolution in MySQL.

This repository contains two independent Maven applications. Start the MCP server and its database before starting the client.

## What It Does

For each new email addressed to `support@example.com`, the client:

1. Polls Mailpit for unread messages (every 10 seconds by default).
2. Sends the email context to the configured OpenAI model, which can call MCP tools to look up customers, orders, products, payments, warranties, and ticket history.
3. When appropriate, records a refund and logs the interaction as a support ticket in MySQL.
4. Sends a threaded email reply through Mailpit. Failed processing leaves the message unread so it can be retried on a later poll.

The MCP server exposes read-only investigation tools as well as action tools for issuing database refunds and logging tickets. Refunds update the demo database; there is no payment processor integration.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `supportAgentMcpClient/` | Spring AI agent, inbox polling, Mailpit integration, SMTP replies, and the `/seed-mail` test endpoint. |
| `supportAgentMcpClient/compose.yml` | Local Mailpit container and persistent inbox data. |
| `supportAgentMcpServer/` | Streamable HTTP MCP server and MySQL/JPA support tools. |
| `supportAgentMcpServer/compose.yml` | Local MySQL container and persistent database volume. |
| `supportAgentMcpServer/db/init/` | MySQL schema and sample data, loaded when MySQL initializes an empty volume. |

Each application has its own `pom.xml` and Maven wrapper. There is no root Maven build.

## Requirements

- Java 25
- Docker with the Docker Compose plugin
- An OpenAI API key with access to the configured chat model
- Network access to download Maven dependencies on the first build

The Maven wrapper is included in both applications, so a separate Maven installation is not required.

## Run Locally

Run the following from the repository root. Keep the server and client application commands running in separate terminals.

### 1. Start MySQL and Mailpit

```sh
docker compose -f supportAgentMcpServer/compose.yml up -d
docker compose -f supportAgentMcpClient/compose.yml up -d
```

MySQL initializes the schema and demo records from `supportAgentMcpServer/db/init/` on its first startup with an empty data volume. Wait for the MySQL health check to pass before starting the MCP server.

### 2. Start the MCP server

In a new terminal from the repository root:

```sh
cd supportAgentMcpServer
./mvnw spring-boot:run
```

The streamable HTTP MCP endpoint is `http://localhost:8090/mcp`.

### 3. Start the email-agent client

In another terminal, set your key in that shell and start the app:

```sh
export OPENAI_API_KEY="your-openai-api-key"
cd supportAgentMcpClient
./mvnw spring-boot:run
```

The client uses `gpt-4o-mini` by default. Do not commit your API key or put it in the application properties file.

### 4. Submit a sample email

Use one of the seeded customer addresses so the agent can find the sender in the demo database. For example, send Priya Sharma a billing question about the seeded duplicate charge:

```sh
curl -X POST \
  --data-urlencode 'from=priya.sharma@example.com' \
  --data-urlencode 'subject=I was charged twice for order 4471' \
  --data-urlencode 'body=Hi, I see two card charges for order 4471. Could you check and help me with the duplicate?' \
  http://localhost:8080/seed-mail
```

The endpoint returns a JSON result indicating whether Mailpit accepted the email. The agent checks for new mail on its next poll, which may take up to 10 seconds. Watch the client logs for the resolution and open [http://localhost:8025](http://localhost:8025) to inspect the incoming message and reply. The MCP server stores its resolution and ticket in MySQL.

Other seeded scenarios include Sarah Mitchell's repeated blender warranty issue (`sarah.mitchell@example.com`, order `4198`), a product-specification question about the `X200`, and Rohan Verma's multilingual request (`rohan.verma@example.com`, order `4502`). See [`02-seed.sql`](supportAgentMcpServer/db/init/02-seed.sql) for all demo records and scenario details.

## Services and Configuration

| Component | Default address | Details |
| --- | --- | --- |
| Client HTTP | `http://localhost:8080` | Includes `POST /seed-mail` for adding a test email. |
| MCP server | `http://localhost:8090/mcp` | Streamable HTTP MCP endpoint consumed by the client. |
| Mailpit UI and REST API | `http://localhost:8025` | Local-only mailbox viewer and API used by the client. |
| Mailpit SMTP | `localhost:1025` | Receives seeded email and agent replies. |
| MySQL | `localhost:3306` | Database `mydatabase`; demo credentials are `myuser` / `secret`. |

Key settings are in each app's `src/main/resources/application.properties`:

- Client: `OPENAI_API_KEY`, model name, support mailbox (`support-agent.inbox.address`), Mailpit URL, poll interval, batch size, and MCP server URL.
- Server: MySQL URL and credentials, server port, and MCP server protocol.

The checked-in MySQL credentials and Mailpit configuration are for local development only. Override the corresponding Spring properties when connecting to services outside this local setup.

## Build and Test

Run each application from its own directory:

```sh
cd supportAgentMcpServer
./mvnw test
./mvnw package
```

```sh
cd supportAgentMcpClient
./mvnw test
./mvnw package
```

The server needs a reachable MySQL instance for its Spring application context. The client requires `OPENAI_API_KEY` to resolve its configured property; starting the full client also requires the MCP server and Mailpit.

## Stop and Reset

Stop the applications with `Ctrl+C`, then stop the containers from the repository root:

```sh
docker compose -f supportAgentMcpClient/compose.yml down
docker compose -f supportAgentMcpServer/compose.yml down
```

This keeps Mailpit's `supportAgentMcpClient/data/` files and the MySQL named volume. To reinitialize MySQL and reload the demo data, remove its volume:

```sh
docker compose -f supportAgentMcpServer/compose.yml down -v
```

**Warning:** `down -v` deletes the local MySQL data, including refunds and tickets created while testing. The next startup will recreate the schema and sample records.

## Safety and Limitations

- This is a local learning/demo project, not a production support system.
- Incoming email content is sent to the configured OpenAI model. Use synthetic data and review the provider's data-handling terms before sending any real customer information.
- The agent is instructed to take actions autonomously. It can create refund and support-ticket rows in MySQL; these are not real payment refunds.
- Mailpit captures mail locally and does not deliver messages to real customers.
- Use known seeded customer email addresses for demo requests. An unknown sender cannot be looked up in the seed database, and processing errors leave messages unread for retry.